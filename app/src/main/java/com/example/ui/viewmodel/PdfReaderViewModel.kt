package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.local.SignatureEntity
import com.example.data.model.AnnotationKind
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfAnnotationItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PdfReaderUiState(
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isContinuousScroll: Boolean = true,
    val isPasswordPromptRequired: Boolean = false,
    val passwordError: Boolean = false,
    val unlockedPassword: String = "",
    val activeTool: AnnotationKind? = null,
    val activeColorArgb: Long = 0xFF0F766E,
    val annotations: List<PdfAnnotationItem> = emptyList(),
    val undoStack: List<List<PdfAnnotationItem>> = emptyList(),
    val redoStack: List<List<PdfAnnotationItem>> = emptyList(),
    val selectedSignatureId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedBanner: String? = null,
    val renderTick: Int = 0
)

class PdfReaderViewModel(
    private val docId: String,
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.documentRepository

    val document: StateFlow<DocumentEntity?> = repo.observeDocument(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val signatures: StateFlow<List<SignatureEntity>> = repo.signatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val renderMutex = Mutex()
    private var workingPdfFile: File? = null
    private var tempDecryptedCopy: File? = null

    private val pageBitmapCache = object : LruCache<Int, Bitmap>(8) {}

    init {
        loadPdf()
    }

    fun loadPdf(password: String = "") {
        viewModelScope.launch {
            val doc = repo.getDocumentById(docId) ?: return@launch
            val readable = repo.getReadablePdfFile(doc)
            if (readable == null || !readable.exists()) {
                _uiState.update { it.copy(error = "PDF file missing") }
                return@launch
            }
            if (doc.isEncrypted) tempDecryptedCopy = readable

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // If password protected, create decrypted working temp copy for PdfRenderer
                    val target = if (doc.isPdfPasswordProtected || password.isNotBlank()) {
                        val tmp = repo.storage.newExportCacheFile("unlock_${doc.id}.pdf")
                        PDDocument.load(readable, password).use { pd ->
                            pd.isAllSecurityToBeRemoved = true
                            pd.save(tmp)
                        }
                        tempDecryptedCopy = tmp
                        tmp
                    } else {
                        readable
                    }
                    val count = repo.pdfEngine.getPdfPageCount(target)
                    target to count
                }
            }

            result.onSuccess { (file, count) ->
                workingPdfFile = file
                pageBitmapCache.evictAll()
                _uiState.update {
                    it.copy(
                        pageCount = count,
                        isPasswordPromptRequired = false,
                        passwordError = false,
                        unlockedPassword = password,
                        renderTick = it.renderTick + 1
                    )
                }
            }.onFailure { err ->
                val isPwdErr = err.message?.contains("password", ignoreCase = true) == true ||
                    doc.isPdfPasswordProtected
                _uiState.update {
                    it.copy(
                        isPasswordPromptRequired = isPwdErr,
                        passwordError = password.isNotBlank() && isPwdErr,
                        error = if (isPwdErr) null else (err.message ?: "Corrupted PDF")
                    )
                }
            }
        }
    }

    suspend fun getOrRenderPage(pageIndex: Int, targetWidthPx: Int = 1100): Bitmap? {
        pageBitmapCache.get(pageIndex)?.let { return it }
        val file = workingPdfFile ?: return null
        return renderMutex.withLock {
            pageBitmapCache.get(pageIndex)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            if (pageIndex !in 0 until renderer.pageCount) return@use null
                            renderer.openPage(pageIndex).use { page ->
                                val scale = (targetWidthPx.toFloat() / page.width.coerceAtLeast(1)).coerceIn(1f, 2.5f)
                                val w = (page.width * scale).toInt()
                                val h = (page.height * scale).toInt()
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmapCache.put(pageIndex, bmp)
                                bmp
                            }
                        }
                    }
                }.getOrNull()
            }
        }
    }

    fun setCurrentPage(idx: Int) = _uiState.update { it.copy(currentPage = idx) }
    fun toggleScrollMode() = _uiState.update { it.copy(isContinuousScroll = !it.isContinuousScroll) }
    fun selectTool(kind: AnnotationKind?) = _uiState.update { it.copy(activeTool = kind) }
    fun selectColor(argb: Long) = _uiState.update { it.copy(activeColorArgb = argb) }

    fun toggleBookmark(pageIndex: Int) {
        viewModelScope.launch { repo.toggleBookmarkPage(docId, pageIndex) }
    }

    fun addAnnotation(item: PdfAnnotationItem) {
        _uiState.update { state ->
            state.copy(
                undoStack = state.undoStack + listOf(state.annotations),
                redoStack = emptyList(),
                annotations = state.annotations + item,
                selectedSignatureId = if (item.kind == AnnotationKind.SIGNATURE) item.id else state.selectedSignatureId
            )
        }
    }

    fun addFreehandStroke(pageIndex: Int, points: List<NormalizedPoint>) {
        if (points.size < 2) return
        addAnnotation(
            PdfAnnotationItem(
                id = UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                kind = AnnotationKind.FREEHAND,
                points = points,
                colorArgb = _uiState.value.activeColorArgb
            )
        )
    }

    fun selectSignatureAnnotation(id: String?) {
        _uiState.update { it.copy(selectedSignatureId = id) }
    }

    fun updateSignatureTransform(id: String, x: Float, y: Float, width: Float, height: Float, rotationDeg: Float) {
        _uiState.update { state ->
            state.copy(
                annotations = state.annotations.map { ann ->
                    if (ann.id == id) {
                        ann.copy(
                            x = x.coerceIn(0f, 0.9f),
                            y = y.coerceIn(0f, 0.9f),
                            width = width.coerceIn(0.08f, 0.7f),
                            height = height.coerceIn(0.04f, 0.45f),
                            rotationDeg = rotationDeg
                        )
                    } else ann
                }
            )
        }
    }

    fun removeAnnotation(id: String) {
        _uiState.update { state ->
            state.copy(
                undoStack = state.undoStack + listOf(state.annotations),
                redoStack = emptyList(),
                annotations = state.annotations.filterNot { it.id == id },
                selectedSignatureId = if (state.selectedSignatureId == id) null else state.selectedSignatureId
            )
        }
    }

    fun undo() {
        _uiState.update { state ->
            val prev = state.undoStack.lastOrNull() ?: return@update state
            state.copy(
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + listOf(state.annotations),
                annotations = prev
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            val next = state.redoStack.lastOrNull() ?: return@update state
            state.copy(
                redoStack = state.redoStack.dropLast(1),
                undoStack = state.undoStack + listOf(state.annotations),
                annotations = next
            )
        }
    }

    fun saveAnnotatedPdf(successBanner: String) {
        val currentAnnotations = _uiState.value.annotations
        if (currentAnnotations.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val doc = repo.getDocumentById(docId) ?: return@launch
            val pdfTarget = File(doc.pdfPath ?: return@launch)
            val input = workingPdfFile ?: pdfTarget
            val outTemp = repo.storage.newExportCacheFile("annotated_${doc.id}.pdf")

            val res = repo.pdfEngine.flattenAnnotationsToPdf(input, currentAnnotations, outTemp, _uiState.value.unlockedPassword)
            if (res.isSuccess) {
                withContext(Dispatchers.IO) {
                    if (doc.isEncrypted) {
                        val enc = repo.security.encryptBytes(outTemp.readBytes())
                        pdfTarget.writeBytes(enc)
                    } else {
                        outTemp.copyTo(pdfTarget, overwrite = true)
                    }
                    repo.storage.secureDeleteFile(outTemp)
                }
                repo.logActivity(doc.id, doc.title, "SIGNED")
                pageBitmapCache.evictAll()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        annotations = emptyList(),
                        undoStack = emptyList(),
                        redoStack = emptyList(),
                        selectedSignatureId = null,
                        savedBanner = successBanner,
                        renderTick = it.renderTick + 1
                    )
                }
                loadPdf(_uiState.value.unlockedPassword)
            } else {
                _uiState.update { it.copy(isSaving = false, error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun consumeBanner() = _uiState.update { it.copy(savedBanner = null, error = null) }

    override fun onCleared() {
        super.onCleared()
        pageBitmapCache.evictAll()
        tempDecryptedCopy?.let { repo.storage.secureDeleteFile(it) }
    }

    class Factory(
        private val docId: String,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PdfReaderViewModel(docId, container) as T
        }
    }
}
