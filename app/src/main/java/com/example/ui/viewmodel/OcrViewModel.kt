package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.model.OcrLanguage
import com.example.data.model.PdfExportOptions
import com.example.pdf.PdfPrintAndShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrUiState(
    val selectedPageIndex: Int = 0,
    val selectedLanguage: OcrLanguage = OcrLanguage.BEN_ENG,
    val editableText: String = "",
    val searchQuery: String = "",
    val isRunning: Boolean = false,
    val progressText: String? = null,
    val message: String? = null
)

class OcrViewModel(
    private val docId: String,
    initialPageIndex: Int,
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.documentRepository
    private val ocrEngine = container.ocrEngine

    val document: StateFlow<DocumentEntity?> = repo.observeDocument(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pages: StateFlow<List<PageEntity>> = repo.observePages(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(OcrUiState(selectedPageIndex = initialPageIndex))
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultLang = container.settingsRepository.settingsFlow.first().defaultOcrLang
            _uiState.update { it.copy(selectedLanguage = defaultLang) }
        }
    }

    fun syncPageText(pageList: List<PageEntity>) {
        val idx = _uiState.value.selectedPageIndex.coerceIn(0, (pageList.size - 1).coerceAtLeast(0))
        val page = pageList.getOrNull(idx) ?: return
        if (_uiState.value.editableText.isEmpty() && page.ocrText.isNotBlank()) {
            _uiState.update { it.copy(editableText = page.ocrText) }
        }
    }

    fun selectPage(index: Int, pageList: List<PageEntity>) {
        val page = pageList.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedPageIndex = index, editableText = page.ocrText) }
    }

    fun selectLanguage(lang: OcrLanguage) = _uiState.update { it.copy(selectedLanguage = lang) }
    fun updateEditableText(text: String) = _uiState.update { it.copy(editableText = text) }
    fun updateSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }

    fun saveEditedText(page: PageEntity) {
        viewModelScope.launch {
            repo.saveOcrForPage(page, _uiState.value.editableText)
            _uiState.update { it.copy(message = "Saved OCR text") }
        }
    }

    fun runOcrCurrentPage(page: PageEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, progressText = "OCR...") }
            val res = ocrEngine.recognizeTextFromFile(page.processedImagePath, _uiState.value.selectedLanguage)
            _uiState.update { it.copy(isRunning = false, progressText = null) }
            res.onSuccess { text ->
                _uiState.update { it.copy(editableText = text) }
                repo.saveOcrForPage(page, text)
            }.onFailure { err ->
                _uiState.update { it.copy(message = err.message ?: "OCR failed") }
            }
        }
    }

    fun runOcrAllPages(pageList: List<PageEntity>) {
        if (pageList.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            val lang = _uiState.value.selectedLanguage
            pageList.forEachIndexed { idx, page ->
                _uiState.update { it.copy(progressText = "${idx + 1} / ${pageList.size}") }
                val res = ocrEngine.recognizeTextFromFile(page.processedImagePath, lang)
                res.onSuccess { txt ->
                    repo.saveOcrForPage(page, txt)
                    if (idx == _uiState.value.selectedPageIndex) {
                        _uiState.update { it.copy(editableText = txt) }
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(isRunning = false, progressText = null, message = err.message) }
                    return@launch
                }
            }
            _uiState.update { it.copy(isRunning = false, progressText = null, message = "OCR completed for all pages") }
        }
    }

    fun exportTxt(context: Context, docTitle: String, chooserTitle: String) {
        viewModelScope.launch {
            val allPages = repo.getPagesOnce(docId)
            val fullText = allPages.mapIndexed { idx, p ->
                "--- Page ${idx + 1} ---\n${if (idx == _uiState.value.selectedPageIndex) _uiState.value.editableText else p.ocrText}"
            }.joinToString("\n\n")
            val txtFile = repo.storage.newExportCacheFile("${docTitle.replace(" ", "_")}.txt")
            withContext(Dispatchers.IO) { txtFile.writeText(fullText, Charsets.UTF_8) }
            PdfPrintAndShareHelper.shareFile(context, txtFile, "text/plain", chooserTitle)
        }
    }

    fun exportSearchablePdf(context: Context, doc: DocumentEntity, chooserTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            val s = container.settingsRepository.settingsFlow.first()
            val res = repo.rebuildPdfForDocument(
                docId,
                PdfExportOptions(
                    title = doc.title,
                    pageSize = s.defaultPageSize,
                    quality = s.defaultQuality,
                    searchableOcr = true
                )
            )
            _uiState.update { it.copy(isRunning = false) }
            res.onSuccess { updated ->
                val f = repo.getReadablePdfFile(updated) ?: return@onSuccess
                val copy = repo.storage.newExportCacheFile("${updated.title}_searchable.pdf")
                withContext(Dispatchers.IO) { f.copyTo(copy, overwrite = true) }
                if (updated.isEncrypted) repo.storage.secureDeleteFile(f)
                PdfPrintAndShareHelper.shareFile(context, copy, "application/pdf", chooserTitle)
            }.onFailure { err ->
                _uiState.update { it.copy(message = err.message) }
            }
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
            return OcrViewModel(docId, initialPageIndex, container) as T
        }
    }
}
