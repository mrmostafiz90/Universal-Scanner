package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupWorker
import com.example.core.AppContainer
import com.example.data.local.ActivityLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.local.PageEntity
import com.example.data.local.SignatureEntity
import com.example.data.model.AppThemeMode
import com.example.data.model.CameraResolution
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import com.example.data.model.OcrLanguage
import com.example.data.model.OcrModelStatus
import com.example.data.model.PdfAnnotationItem
import com.example.data.model.PdfExportOptions
import com.example.data.model.PdfPageSize
import com.example.data.model.SortOrder
import com.example.data.model.ViewMode
import com.example.data.preferences.UserSettings
import com.example.localization.AppLanguage
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocLibraryTab {
    ALL, RECENT, FAVORITES, STARRED, TRASH
}

data class MainUiState(
    val isAppLocked: Boolean = false,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val storageUsedBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val selectedFolderId: String? = null,
    val libraryTab: DocLibraryTab = DocLibraryTab.ALL,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.MODIFIED_DESC,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedDocIds: Set<String> = emptySet(),
    val ocrModels: List<OcrModelStatus> = emptyList(),
    val modelDownloadProgress: Map<String, Float> = emptyMap(),
    val driveSyncProgress: Pair<Int, Int>? = null,
    val snackbarEvent: String? = null
)

class MainViewModel(
    val container: AppContainer
) : ViewModel() {

    private val repo = container.documentRepository
    private val settingsRepo = container.settingsRepository
    private val ocrEngine = container.ocrEngine
    private val driveService = container.driveService

    val settings: StateFlow<UserSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val activeDocuments: StateFlow<List<DocumentEntity>> = repo.activeDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedDocuments: StateFlow<List<DocumentEntity>> = repo.deletedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repo.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalActivePages: StateFlow<Int> = repo.totalActivePages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val signatures: StateFlow<List<SignatureEntity>> = repo.signatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = repo.backupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentActivity: StateFlow<List<ActivityLogEntity>> = repo.recentActivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.settingsFlow.first()
            if (s.pinEnabled && s.pinHash.isNotBlank()) {
                _uiState.update { it.copy(isAppLocked = true) }
            }
            refreshStorageMetrics()
            refreshOcrModels()
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarEvent = null) }
    }

    fun postMessage(msg: String) {
        _uiState.update { it.copy(snackbarEvent = msg) }
    }

    fun refreshStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val used = repo.storage.calculateUsedStorageBytes()
            val temp = repo.storage.calculateTempCacheBytes()
            _uiState.update { it.copy(storageUsedBytes = used, tempCacheBytes = temp) }
        }
    }

    fun refreshOcrModels() {
        viewModelScope.launch {
            val models = ocrEngine.getModelStatuses()
            _uiState.update { it.copy(ocrModels = models) }
        }
    }

    // Security & Lock
    fun verifyPinAndUnlock(pin: String): Boolean {
        val s = settings.value
        val ok = repo.security.verifyPin(pin, s.pinHash, s.pinSalt)
        if (ok) _uiState.update { it.copy(isAppLocked = false) }
        return ok
    }

    fun unlockWithBiometric() {
        _uiState.update { it.copy(isAppLocked = false) }
    }

    fun setPinLock(pin: String) {
        viewModelScope.launch {
            val (hash, salt) = repo.security.hashPin(pin)
            settingsRepo.setPin(true, hash, salt)
        }
    }

    fun disablePinLock() {
        viewModelScope.launch { settingsRepo.disablePin() }
    }

    // Library filtering & selection
    fun setLibraryTab(tab: DocLibraryTab) = _uiState.update { it.copy(libraryTab = tab, selectedDocIds = emptySet()) }
    fun selectFolder(folderId: String?) = _uiState.update { it.copy(selectedFolderId = folderId, selectedDocIds = emptySet()) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setSortOrder(order: SortOrder) = _uiState.update { it.copy(sortOrder = order) }
    fun toggleViewMode() = _uiState.update {
        it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
    }

    fun toggleDocSelection(docId: String) = _uiState.update { state ->
        val next = state.selectedDocIds.toMutableSet()
        if (!next.add(docId)) next.remove(docId)
        state.copy(selectedDocIds = next)
    }

    fun selectAllDocs(ids: List<String>) = _uiState.update { it.copy(selectedDocIds = ids.toSet()) }
    fun clearSelection() = _uiState.update { it.copy(selectedDocIds = emptySet()) }

    // Folders
    fun createFolder(name: String, parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.createFolder(name, parentId) }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { repo.renameFolder(id, name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            if (_uiState.value.selectedFolderId == id) {
                _uiState.update { it.copy(selectedFolderId = null) }
            }
            repo.deleteFolder(id)
        }
    }

    // Document creation & import
    fun createDocumentFromCapturedFiles(
        files: List<File>,
        customTitle: String? = null,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (!repo.storage.hasSufficientStorage()) {
                postMessage("Insufficient storage available")
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, busyMessage = "Processing scan...") }
            val s = settings.value
            val title = customTitle?.takeIf { it.isNotBlank() } ?: defaultScanTitle()
            val res = repo.createDocumentFromCapturedImages(
                title = title,
                sourceFiles = files,
                defaultAdjustments = ImageAdjustments(filterType = s.defaultFilter),
                autoCrop = s.autoCrop,
                exportOptions = PdfExportOptions(
                    title = title,
                    pageSize = s.defaultPageSize,
                    quality = s.defaultQuality
                ),
                folderId = _uiState.value.selectedFolderId,
                encryptLocal = s.localEncryptionEnabled
            )
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            refreshStorageMetrics()
            res.onSuccess { doc -> onCreated(doc.id) }
                .onFailure { err -> postMessage(err.message ?: "Scan processing failed") }
        }
    }

    fun appendCapturedFilesToDocument(
        docId: String,
        files: List<File>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Adding pages...") }
            val s = settings.value
            val res = repo.addPagesToDocument(
                docId = docId,
                newImageFiles = files,
                defaultAdjustments = ImageAdjustments(filterType = s.defaultFilter),
                autoCrop = s.autoCrop
            )
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            refreshStorageMetrics()
            res.onSuccess { onDone() }
                .onFailure { postMessage(it.message ?: "Failed to add pages") }
        }
    }

    fun importGalleryImages(uris: List<Uri>, onCreated: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Importing ${uris.size} images...") }
            val s = settings.value
            val title = defaultScanTitle("Gallery")
            val res = repo.createDocumentFromGalleryUris(
                title = title,
                uris = uris,
                defaultAdjustments = ImageAdjustments(filterType = s.defaultFilter),
                autoCrop = s.autoCrop,
                exportOptions = PdfExportOptions(
                    title = title,
                    pageSize = s.defaultPageSize,
                    quality = s.defaultQuality
                ),
                folderId = _uiState.value.selectedFolderId,
                encryptLocal = s.localEncryptionEnabled
            )
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            refreshStorageMetrics()
            res.onSuccess { onCreated(it.id) }
                .onFailure { postMessage(it.message ?: "Image import failed") }
        }
    }

    fun importPdfFromUri(uri: Uri, displayName: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Importing PDF...") }
            val res = repo.importExternalPdf(uri, displayName.removeSuffix(".pdf"), _uiState.value.selectedFolderId)
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            refreshStorageMetrics()
            res.onSuccess { onCreated(it.id) }
                .onFailure { postMessage(it.message ?: "Invalid or protected PDF") }
        }
    }

    // Document CRUD
    fun renameDocument(docId: String, title: String) {
        viewModelScope.launch { repo.renameDocument(docId, title) }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch { repo.toggleFavorite(doc) }
    }

    fun toggleStarred(doc: DocumentEntity) {
        viewModelScope.launch { repo.toggleStarred(doc) }
    }

    fun duplicateDocument(docId: String) {
        viewModelScope.launch {
            repo.duplicateDocument(docId)
            refreshStorageMetrics()
        }
    }

    fun moveDocuments(ids: List<String>, targetFolderId: String?) {
        viewModelScope.launch {
            repo.moveDocuments(ids, targetFolderId)
            clearSelection()
        }
    }

    fun softDelete(ids: List<String>) {
        viewModelScope.launch {
            repo.softDelete(ids)
            clearSelection()
        }
    }

    fun restoreDocuments(ids: List<String>) {
        viewModelScope.launch {
            repo.restore(ids)
            clearSelection()
        }
    }

    fun hardDelete(ids: List<String>) {
        viewModelScope.launch {
            repo.hardDelete(ids)
            clearSelection()
            refreshStorageMetrics()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repo.emptyTrash()
            refreshStorageMetrics()
        }
    }

    // Export & Share helpers
    fun exportDocumentToDownloads(context: Context, doc: DocumentEntity, successMsg: (String) -> String) {
        viewModelScope.launch {
            val file = repo.getReadablePdfFile(doc) ?: return@launch
            val outName = "${doc.title.replace(" ", "_")}.pdf"
            val res = withContext(Dispatchers.IO) {
                PdfPrintAndShareHelper.saveToDownloads(context, file, outName, "application/pdf")
            }
            if (doc.isEncrypted) repo.storage.secureDeleteFile(file)
            res.onSuccess { postMessage(successMsg(it)) }
                .onFailure { postMessage(it.message ?: "Export failed") }
        }
    }

    fun shareDocumentAsPdf(context: Context, doc: DocumentEntity, chooserTitle: String) {
        viewModelScope.launch {
            val file = repo.getReadablePdfFile(doc) ?: return@launch
            val shareCopy = repo.storage.newExportCacheFile("${doc.title}.pdf")
            withContext(Dispatchers.IO) { file.copyTo(shareCopy, overwrite = true) }
            if (doc.isEncrypted) repo.storage.secureDeleteFile(file)
            PdfPrintAndShareHelper.shareFile(context, shareCopy, "application/pdf", chooserTitle)
        }
    }

    fun shareDocumentAsImages(context: Context, doc: DocumentEntity, asPng: Boolean, chooserTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Rendering images...") }
            val file = repo.getReadablePdfFile(doc)
            if (file == null) {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                return@launch
            }
            val fmt = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val mime = if (asPng) "image/png" else "image/jpeg"
            val res = repo.pdfEngine.exportPdfToImages(file, fmt, doc.title)
            if (doc.isEncrypted) repo.storage.secureDeleteFile(file)
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            res.onSuccess { images ->
                PdfPrintAndShareHelper.shareMultipleFiles(context, images, mime, chooserTitle)
            }.onFailure { postMessage(it.message ?: "Image export failed") }
        }
    }

    fun shareBulkDocuments(context: Context, docs: List<DocumentEntity>, chooserTitle: String) {
        viewModelScope.launch {
            val files = docs.mapNotNull { doc ->
                val f = repo.getReadablePdfFile(doc) ?: return@mapNotNull null
                val copy = repo.storage.newExportCacheFile("${doc.title}.pdf")
                withContext(Dispatchers.IO) { f.copyTo(copy, overwrite = true) }
                if (doc.isEncrypted) repo.storage.secureDeleteFile(f)
                copy
            }
            PdfPrintAndShareHelper.shareMultipleFiles(context, files, "application/pdf", chooserTitle)
        }
    }

    // PDF Tools
    fun mergeSelectedPdfs(docs: List<DocumentEntity>, newTitle: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Merging PDFs...") }
            val tempCopies = mutableListOf<File>()
            val inputs = docs.mapNotNull { doc ->
                val f = repo.getReadablePdfFile(doc) ?: return@mapNotNull null
                if (doc.isEncrypted) tempCopies.add(f)
                f
            }
            val out = repo.storage.newExportCacheFile("merged_${System.currentTimeMillis()}.pdf")
            val res = repo.pdfEngine.mergePdfs(inputs, out)
            tempCopies.forEach { repo.storage.secureDeleteFile(it) }
            if (res.isSuccess) {
                val created = repo.registerGeneratedPdfDocument(newTitle.ifBlank { "Merged PDF" }, out)
                repo.storage.secureDeleteFile(out)
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                refreshStorageMetrics()
                onComplete(created.id)
            } else {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage(res.exceptionOrNull()?.message ?: "Merge failed")
            }
        }
    }

    fun splitPdf(doc: DocumentEntity, rangeSpec: String, newTitle: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Splitting PDF...") }
            val src = repo.getReadablePdfFile(doc)
            if (src == null) {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage("PDF file not found")
                return@launch
            }
            val out = repo.storage.newExportCacheFile("split_${System.currentTimeMillis()}.pdf")
            val res = repo.pdfEngine.splitPdfByRange(src, rangeSpec, out)
            if (doc.isEncrypted) repo.storage.secureDeleteFile(src)
            if (res.isSuccess) {
                val created = repo.registerGeneratedPdfDocument(newTitle.ifBlank { "${doc.title} ($rangeSpec)" }, out)
                repo.storage.secureDeleteFile(out)
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                refreshStorageMetrics()
                onComplete(created.id)
            } else {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage(res.exceptionOrNull()?.message ?: "Split failed")
            }
        }
    }

    fun compressPdf(doc: DocumentEntity, quality: Int, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Compressing PDF...") }
            val src = repo.getReadablePdfFile(doc)
            if (src == null) {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage("PDF file not found")
                return@launch
            }
            val out = repo.storage.newExportCacheFile("compressed_${System.currentTimeMillis()}.pdf")
            val res = repo.pdfEngine.compressPdf(src, quality, out)
            if (doc.isEncrypted) repo.storage.secureDeleteFile(src)
            if (res.isSuccess) {
                val created = repo.registerGeneratedPdfDocument("${doc.title} (Compressed)", out)
                repo.storage.secureDeleteFile(out)
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                refreshStorageMetrics()
                onComplete(created.id)
            } else {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage(res.exceptionOrNull()?.message ?: "Compression failed")
            }
        }
    }

    fun protectOrWatermarkPdf(
        doc: DocumentEntity,
        watermark: String,
        includePageNums: Boolean,
        password: String,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Updating PDF...") }
            val src = repo.getReadablePdfFile(doc)
            if (src == null) {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage("PDF file not found")
                return@launch
            }
            val out = repo.storage.newExportCacheFile("secured_${System.currentTimeMillis()}.pdf")
            val res = repo.pdfEngine.applyWatermarkAndEncryption(src, watermark, includePageNums, password, out)
            if (doc.isEncrypted) repo.storage.secureDeleteFile(src)
            if (res.isSuccess) {
                val created = repo.registerGeneratedPdfDocument(
                    title = if (password.isNotBlank()) "${doc.title} (Protected)" else "${doc.title} (Stamped)",
                    pdfFile = out,
                    isPasswordProtected = password.isNotBlank()
                )
                repo.storage.secureDeleteFile(out)
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                refreshStorageMetrics()
                onComplete(created.id)
            } else {
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                postMessage(res.exceptionOrNull()?.message ?: "PDF update failed")
            }
        }
    }

    // Signatures
    fun saveSignature(name: String, bitmap: Bitmap) {
        viewModelScope.launch {
            repo.saveSignature(name, bitmap)
        }
    }

    fun deleteSignature(sig: SignatureEntity) {
        viewModelScope.launch { repo.deleteSignature(sig) }
    }

    // OCR Models
    fun downloadOcrModel(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelDownloadProgress = it.modelDownloadProgress + (code to 0.01f)) }
            val res = ocrEngine.downloadLanguageModel(code) { p ->
                _uiState.update { it.copy(modelDownloadProgress = it.modelDownloadProgress + (code to p)) }
            }
            _uiState.update { it.copy(modelDownloadProgress = it.modelDownloadProgress - code) }
            refreshOcrModels()
            res.onFailure { postMessage(it.message ?: "Model download failed") }
        }
    }

    fun deleteOcrModel(code: String) {
        viewModelScope.launch {
            ocrEngine.deleteLanguageModel(code)
            refreshOcrModels()
        }
    }

    // Settings
    fun setLanguage(lang: AppLanguage) { viewModelScope.launch { settingsRepo.setLanguage(lang) } }
    fun setThemeMode(mode: AppThemeMode) { viewModelScope.launch { settingsRepo.setThemeMode(mode) } }
    fun setDefaultPageSize(size: PdfPageSize) { viewModelScope.launch { settingsRepo.setDefaultPageSize(size) } }
    fun setDefaultQuality(q: Int) { viewModelScope.launch { settingsRepo.setDefaultQuality(q) } }
    fun setDefaultFilter(f: FilterType) { viewModelScope.launch { settingsRepo.setDefaultFilter(f) } }
    fun setAutoCapture(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoCapture(enabled) } }
    fun setAutoCrop(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoCrop(enabled) } }
    fun setDefaultOcrLang(lang: OcrLanguage) { viewModelScope.launch { settingsRepo.setDefaultOcrLang(lang) } }
    fun setCameraResolution(res: CameraResolution) { viewModelScope.launch { settingsRepo.setCameraResolution(res) } }
    fun setBiometricEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setBiometricEnabled(enabled) } }
    fun setLocalEncryptionEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setLocalEncryptionEnabled(enabled) } }

    fun clearTempFiles(doneMsg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.storage.securelyWipeTempFiles()
            refreshStorageMetrics()
            postMessage(doneMsg)
        }
    }

    // Google Drive Backup & Restore
    fun setDriveAccountEmail(email: String?) {
        viewModelScope.launch { settingsRepo.setDriveAccount(email) }
    }

    fun setAutoBackup(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAutoBackupEnabled(enabled)
            BackupWorker.scheduleDailyBackup(context, enabled)
        }
    }

    fun setIncludeSettingsInBackup(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setIncludeSettingsInBackup(enabled) }
    }

    fun runDriveBackup(accessToken: String, folderIds: Set<String>?, includeSettings: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(driveSyncProgress = 0 to 1) }
            val res = driveService.backupDocuments(accessToken, folderIds, includeSettings) { done, total ->
                _uiState.update { it.copy(driveSyncProgress = done to total) }
            }
            _uiState.update { it.copy(driveSyncProgress = null) }
            res.onSuccess { count -> postMessage("Backed up $count documents to Google Drive") }
                .onFailure { postMessage(it.message ?: "Drive backup failed") }
        }
    }

    fun runDriveRestore(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(driveSyncProgress = 0 to 1) }
            val res = driveService.restoreFromDrive(accessToken) { done, total ->
                _uiState.update { it.copy(driveSyncProgress = done to total) }
            }
            _uiState.update { it.copy(driveSyncProgress = null) }
            refreshStorageMetrics()
            res.onSuccess { count -> postMessage("Restored $count documents from Google Drive") }
                .onFailure { postMessage(it.message ?: "Drive restore failed") }
        }
    }

    fun deleteDriveBackup(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Deleting Drive backup...") }
            val res = driveService.deleteRemoteBackup(accessToken)
            _uiState.update { it.copy(isBusy = false, busyMessage = null) }
            res.onSuccess { postMessage("Cloud backup deleted") }
                .onFailure { postMessage(it.message ?: "Failed to delete cloud backup") }
        }
    }

    private fun defaultScanTitle(prefix: String = "Scan"): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
        return "$prefix ${fmt.format(Date())}"
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
