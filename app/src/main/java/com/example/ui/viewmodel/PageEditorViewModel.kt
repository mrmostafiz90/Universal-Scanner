package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import com.example.data.model.PdfExportOptions
import com.example.scanner.DocumentEdgeDetector
import com.example.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class PageEditorUiState(
    val currentPageIndex: Int = 0,
    val workingAdjustments: ImageAdjustments = ImageAdjustments(),
    val previewBitmap: Bitmap? = null,
    val originalPreviewBitmap: Bitmap? = null,
    val filterThumbnails: Map<FilterType, Bitmap> = emptyMap(),
    val isProcessing: Boolean = false,
    val isCropMode: Boolean = false,
    val isShowingOriginal: Boolean = false,
    val message: String? = null
)

class PageEditorViewModel(
    private val docId: String,
    initialPageIndex: Int,
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.documentRepository
    private val json = Json { ignoreUnknownKeys = true }

    val document: StateFlow<DocumentEntity?> = repo.observeDocument(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pages: StateFlow<List<PageEntity>> = repo.observePages(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PageEditorUiState(currentPageIndex = initialPageIndex))
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private var loadedPageId: String? = null

    fun onPagesUpdated(pageList: List<PageEntity>) {
        if (pageList.isEmpty()) return
        val safeIdx = _uiState.value.currentPageIndex.coerceIn(0, pageList.lastIndex)
        val page = pageList[safeIdx]
        if (page.id != loadedPageId) {
            loadPage(safeIdx, page)
        }
    }

    fun selectPage(index: Int, pageList: List<PageEntity>) {
        if (index !in pageList.indices) return
        loadPage(index, pageList[index])
    }

    private fun loadPage(index: Int, page: PageEntity) {
        loadedPageId = page.id
        val adj = runCatching { json.decodeFromString<ImageAdjustments>(page.adjustmentsJson) }
            .getOrDefault(ImageAdjustments())
        _uiState.update { it.copy(currentPageIndex = index, workingAdjustments = adj, isCropMode = false) }
        renderPreview(page, adj)
        generateFilterStrip(page, adj)
    }

    private fun renderPreview(page: PageEntity, adj: ImageAdjustments) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val (procBmp, origBmp) = withContext(Dispatchers.Default) {
                val raw = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 1280)
                    ?: return@withContext null to null
                val processed = ImageProcessor.applyAdjustments(raw, adj, 1280)
                val warpedOrig = if (adj.cropQuad.isFullFrame()) {
                    raw
                } else {
                    ImageProcessor.perspectiveWarp(raw, adj.cropQuad, 1280)
                }
                processed to warpedOrig
            }
            _uiState.update {
                it.copy(
                    previewBitmap = procBmp,
                    originalPreviewBitmap = origBmp,
                    isProcessing = false
                )
            }
        }
    }

    private fun generateFilterStrip(page: PageEntity, baseAdj: ImageAdjustments) {
        viewModelScope.launch(Dispatchers.Default) {
            val tiny = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 180) ?: return@launch
            val map = linkedMapOf<FilterType, Bitmap>()
            FilterType.entries.forEach { ft ->
                val thumbAdj = baseAdj.copy(filterType = ft, intensity = 1f)
                map[ft] = ImageProcessor.applyAdjustments(tiny, thumbAdj, 180)
            }
            tiny.recycle()
            _uiState.update { it.copy(filterThumbnails = map) }
        }
    }

    fun updateAdjustments(page: PageEntity, transform: (ImageAdjustments) -> ImageAdjustments) {
        val next = transform(_uiState.value.workingAdjustments)
        _uiState.update { it.copy(workingAdjustments = next) }
        renderPreview(page, next)
    }

    fun rotate90(page: PageEntity) {
        updateAdjustments(page) { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleCropMode(enabled: Boolean) {
        _uiState.update { it.copy(isCropMode = enabled) }
    }

    fun setCropQuad(page: PageEntity, quad: CropQuad) {
        updateAdjustments(page) { it.copy(cropQuad = quad) }
    }

    fun autoDetectCrop(page: PageEntity) {
        viewModelScope.launch(Dispatchers.Default) {
            val raw = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 600) ?: return@launch
            val detected = DocumentEdgeDetector.detectFromBitmap(raw).quad
            raw.recycle()
            updateAdjustments(page) { it.copy(cropQuad = detected) }
        }
    }

    fun setShowingOriginal(show: Boolean) {
        _uiState.update { it.copy(isShowingOriginal = show) }
    }

    fun saveCurrentPageEdits(page: PageEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val res = repo.updatePageAdjustments(page, _uiState.value.workingAdjustments)
            _uiState.update { it.copy(isProcessing = false) }
            res.onSuccess { onSaved() }
                .onFailure { err -> _uiState.update { it.copy(message = err.message) } }
        }
    }

    fun applyFilterToAllPages(pages: List<PageEntity>, doneMessage: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val currentAdj = _uiState.value.workingAdjustments
            pages.forEach { p ->
                val pAdj = runCatching { json.decodeFromString<ImageAdjustments>(p.adjustmentsJson) }
                    .getOrDefault(ImageAdjustments())
                val merged = currentAdj.copy(
                    cropQuad = pAdj.cropQuad,
                    rotationDegrees = pAdj.rotationDegrees
                )
                repo.updatePageAdjustments(p, merged)
            }
            _uiState.update { it.copy(isProcessing = false, message = doneMessage) }
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            repo.deletePageFromDocument(page)
            loadedPageId = null
            _uiState.update { it.copy(currentPageIndex = (it.currentPageIndex - 1).coerceAtLeast(0)) }
        }
    }

    fun movePage(pages: List<PageEntity>, fromIdx: Int, toIdx: Int) {
        if (fromIdx !in pages.indices || toIdx !in pages.indices) return
        val mutable = pages.toMutableList()
        val item = mutable.removeAt(fromIdx)
        mutable.add(toIdx, item)
        viewModelScope.launch {
            repo.reorderPages(docId, mutable)
            _uiState.update { it.copy(currentPageIndex = toIdx) }
        }
    }

    fun exportPdf(
        currentPage: PageEntity?,
        options: PdfExportOptions,
        onExported: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            if (currentPage != null) {
                repo.updatePageAdjustments(currentPage, _uiState.value.workingAdjustments)
            }
            val res = repo.rebuildPdfForDocument(docId, options)
            _uiState.update { it.copy(isProcessing = false) }
            res.onSuccess { onExported(it.id) }
                .onFailure { err -> _uiState.update { it.copy(message = err.message) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    class Factory(
        private val docId: String,
        private val initialPageIndex: Int,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PageEditorViewModel(docId, initialPageIndex, container) as T
        }
    }
}
