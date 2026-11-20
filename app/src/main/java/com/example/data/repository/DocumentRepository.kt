package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.FileStorageManager
import com.example.data.local.ActivityLogDao
import com.example.data.local.ActivityLogEntity
import com.example.data.local.BackupHistoryDao
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderDao
import com.example.data.local.FolderEntity
import com.example.data.local.PageDao
import com.example.data.local.PageEntity
import com.example.data.local.SignatureDao
import com.example.data.local.SignatureEntity
import com.example.data.model.ImageAdjustments
import com.example.data.model.PdfExportOptions
import com.example.pdf.PageInput
import com.example.pdf.PdfEngine
import com.example.scanner.DocumentEdgeDetector
import com.example.scanner.ImageProcessor
import com.example.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao,
    private val pageDao: PageDao,
    private val signatureDao: SignatureDao,
    private val backupHistoryDao: BackupHistoryDao,
    private val activityLogDao: ActivityLogDao,
    val storage: FileStorageManager,
    val pdfEngine: PdfEngine,
    val security: SecurityManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    val activeDocuments: Flow<List<DocumentEntity>> = documentDao.observeActiveDocuments()
    val deletedDocuments: Flow<List<DocumentEntity>> = documentDao.observeDeletedDocuments()
    val allFolders: Flow<List<FolderEntity>> = folderDao.observeAllFolders()
    val totalActivePages: Flow<Int> = pageDao.observeTotalActivePageCount()
    val signatures: Flow<List<SignatureEntity>> = signatureDao.observeSignatures()
    val backupHistory: Flow<List<BackupHistoryEntity>> = backupHistoryDao.observeHistory()
    val recentActivity: Flow<List<ActivityLogEntity>> = activityLogDao.observeRecentActivity()

    fun observeDocument(docId: String): Flow<DocumentEntity?> = documentDao.observeDocumentById(docId)
    fun observePages(docId: String): Flow<List<PageEntity>> = pageDao.observePagesForDocument(docId)

    suspend fun getDocumentById(docId: String): DocumentEntity? = documentDao.getDocumentById(docId)
    suspend fun getPagesOnce(docId: String): List<PageEntity> = pageDao.getPagesForDocumentOnce(docId)

    suspend fun createFolder(name: String, parentFolderId: String? = null, colorHex: Long = 0xFF0F766E): FolderEntity {
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            parentFolderId = parentFolderId,
            colorHex = colorHex
        )
        folderDao.upsertFolder(folder)
        return folder
    }

    suspend fun renameFolder(id: String, newName: String) {
        if (newName.isNotBlank()) folderDao.renameFolder(id, newName.trim())
    }

    suspend fun deleteFolder(id: String) {
        documentDao.detachFolderFromDocuments(id)
        folderDao.deleteFolder(id)
    }

    suspend fun createDocumentFromCapturedImages(
        title: String,
        sourceFiles: List<File>,
        defaultAdjustments: ImageAdjustments,
        autoCrop: Boolean,
        exportOptions: PdfExportOptions,
        folderId: String? = null,
        encryptLocal: Boolean = false
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val docId = UUID.randomUUID().toString()
            val pageEntities = mutableListOf<PageEntity>()

            sourceFiles.forEachIndexed { idx, srcFile ->
                val pageId = UUID.randomUUID().toString()
                val origDest = storage.newOriginalPageFile(docId, pageId)
                srcFile.copyTo(origDest, overwrite = true)

                val bmp = ImageProcessor.decodeSampledBitmap(origDest.absolutePath, 2200)
                    ?: error("Failed to decode image")
                val detectedQuad = if (autoCrop) {
                    DocumentEdgeDetector.detectFromBitmap(bmp).quad
                } else {
                    defaultAdjustments.cropQuad
                }
                val adj = defaultAdjustments.copy(cropQuad = detectedQuad)
                val processed = ImageProcessor.applyAdjustments(bmp, adj)
                val procDest = storage.newProcessedPageFile(docId, pageId)
                storage.saveBitmapAtomic(processed, procDest, Bitmap.CompressFormat.JPEG, 92)
                val thumb = storage.saveThumbnail(processed, pageId)

                val entity = PageEntity(
                    id = pageId,
                    documentId = docId,
                    pageIndex = idx,
                    originalImagePath = origDest.absolutePath,
                    processedImagePath = procDest.absolutePath,
                    thumbnailPath = thumb.absolutePath,
                    adjustmentsJson = json.encodeToString(adj),
                    widthPx = processed.width,
                    heightPx = processed.height
                )
                pageEntities.add(entity)
                if (processed !== bmp) processed.recycle()
                bmp.recycle()
            }

            val pdfFile = storage.newPdfFile(docId)
            val inputs = pageEntities.map { PageInput(it.processedImagePath, it.ocrText) }
            pdfEngine.createPdfFromPages(inputs, pdfFile, exportOptions.copy(title = title)).getOrThrow()

            if (encryptLocal) {
                security.encryptFileInPlace(pdfFile)
            }

            val docThumb = pageEntities.firstOrNull()?.thumbnailPath
            val doc = DocumentEntity(
                id = docId,
                title = title,
                folderId = folderId,
                pageCount = pageEntities.size,
                totalSizeBytes = pdfFile.length(),
                thumbnailPath = docThumb,
                pdfPath = pdfFile.absolutePath,
                isEncrypted = encryptLocal,
                isPdfPasswordProtected = exportOptions.password.isNotBlank()
            )
            documentDao.upsertDocument(doc)
            pageDao.upsertPages(pageEntities)
            logActivity(docId, title, "SCANNED")
            doc
        }
    }

    suspend fun createDocumentFromGalleryUris(
        title: String,
        uris: List<Uri>,
        defaultAdjustments: ImageAdjustments,
        autoCrop: Boolean,
        exportOptions: PdfExportOptions,
        folderId: String? = null,
        encryptLocal: Boolean = false
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFiles = uris.mapNotNull { uri ->
                val f = storage.newTempCameraFile()
                val ok = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(f).use { output -> input.copyTo(output) }
                    }
                }.isSuccess
                if (ok && f.exists() && f.length() > 0) f else null
            }
            require(tempFiles.isNotEmpty()) { "No readable images selected" }
            val result = createDocumentFromCapturedImages(
                title = title,
                sourceFiles = tempFiles,
                defaultAdjustments = defaultAdjustments,
                autoCrop = autoCrop,
                exportOptions = exportOptions,
                folderId = folderId,
                encryptLocal = encryptLocal
            )
            tempFiles.forEach { storage.secureDeleteFile(it) }
            result.getOrThrow()
        }
    }

    suspend fun importExternalPdf(
        uri: Uri,
        title: String,
        folderId: String? = null
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val docId = UUID.randomUUID().toString()
            val pdfFile = storage.newPdfFile(docId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
            } ?: error("Unable to open PDF")

            val pageImages = pdfEngine.importExternalPdfToImages(uri, docId).getOrThrow()
            val pages = pageImages.mapIndexed { idx, imgFile ->
                val pageId = UUID.randomUUID().toString()
                val procFile = storage.newProcessedPageFile(docId, pageId)
                imgFile.copyTo(procFile, overwrite = true)
                val bmp = ImageProcessor.decodeSampledBitmap(procFile.absolutePath, 1600)
                val thumbPath = bmp?.let {
                    val t = storage.saveThumbnail(it, pageId)
                    val w = it.width; val h = it.height
                    it.recycle()
                    Triple(t.absolutePath, w, h)
                } ?: Triple(procFile.absolutePath, 1000, 1400)

                PageEntity(
                    id = pageId,
                    documentId = docId,
                    pageIndex = idx,
                    originalImagePath = imgFile.absolutePath,
                    processedImagePath = procFile.absolutePath,
                    thumbnailPath = thumbPath.first,
                    adjustmentsJson = json.encodeToString(ImageAdjustments()),
                    widthPx = thumbPath.second,
                    heightPx = thumbPath.third
                )
            }

            val doc = DocumentEntity(
                id = docId,
                title = title,
                folderId = folderId,
                pageCount = pages.size,
                totalSizeBytes = pdfFile.length(),
                thumbnailPath = pages.firstOrNull()?.thumbnailPath,
                pdfPath = pdfFile.absolutePath
            )
            documentDao.upsertDocument(doc)
            pageDao.upsertPages(pages)
            logActivity(docId, title, "PDF_EXPORTED")
            doc
        }
    }

    suspend fun updatePageAdjustments(
        page: PageEntity,
        newAdjustments: ImageAdjustments
    ): Result<PageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val orig = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 2200)
                ?: error("Original page image missing")
            val processed = ImageProcessor.applyAdjustments(orig, newAdjustments)
            val procFile = File(page.processedImagePath)
            storage.saveBitmapAtomic(processed, procFile, Bitmap.CompressFormat.JPEG, 92)
            val thumb = storage.saveThumbnail(processed, page.id)

            val updated = page.copy(
                adjustmentsJson = json.encodeToString(newAdjustments),
                thumbnailPath = thumb.absolutePath,
                widthPx = processed.width,
                heightPx = processed.height,
                updatedAt = System.currentTimeMillis()
            )
            if (processed !== orig) processed.recycle()
            orig.recycle()

            pageDao.upsertPage(updated)
            val doc = documentDao.getDocumentById(page.documentId)
            if (doc != null && page.pageIndex == 0) {
                documentDao.updateDocument(doc.copy(thumbnailPath = updated.thumbnailPath, updatedAt = System.currentTimeMillis()))
            }
            updated
        }
    }

    suspend fun addPagesToDocument(
        docId: String,
        newImageFiles: List<File>,
        defaultAdjustments: ImageAdjustments,
        autoCrop: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = pageDao.getPagesForDocumentOnce(docId)
            var nextIdx = (existing.maxOfOrNull { it.pageIndex } ?: -1) + 1
            val added = mutableListOf<PageEntity>()

            newImageFiles.forEach { src ->
                val pageId = UUID.randomUUID().toString()
                val origDest = storage.newOriginalPageFile(docId, pageId)
                src.copyTo(origDest, overwrite = true)

                val bmp = ImageProcessor.decodeSampledBitmap(origDest.absolutePath, 2200)
                    ?: error("Invalid image")
                val quad = if (autoCrop) DocumentEdgeDetector.detectFromBitmap(bmp).quad else defaultAdjustments.cropQuad
                val adj = defaultAdjustments.copy(cropQuad = quad)
                val proc = ImageProcessor.applyAdjustments(bmp, adj)
                val procDest = storage.newProcessedPageFile(docId, pageId)
                storage.saveBitmapAtomic(proc, procDest, Bitmap.CompressFormat.JPEG, 92)
                val thumb = storage.saveThumbnail(proc, pageId)

                added.add(
                    PageEntity(
                        id = pageId,
                        documentId = docId,
                        pageIndex = nextIdx++,
                        originalImagePath = origDest.absolutePath,
                        processedImagePath = procDest.absolutePath,
                        thumbnailPath = thumb.absolutePath,
                        adjustmentsJson = json.encodeToString(adj),
                        widthPx = proc.width,
                        heightPx = proc.height
                    )
                )
                if (proc !== bmp) proc.recycle()
                bmp.recycle()
            }
            pageDao.upsertPages(added)
            rebuildPdfForDocument(docId, PdfExportOptions())
            Unit
        }
    }

    suspend fun deletePageFromDocument(page: PageEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            pageDao.deletePageById(page.id)
            storage.secureDeleteFile(File(page.originalImagePath))
            storage.secureDeleteFile(File(page.processedImagePath))
            storage.secureDeleteFile(File(page.thumbnailPath))

            val remaining = pageDao.getPagesForDocumentOnce(page.documentId)
            val reindexed = remaining.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
            pageDao.upsertPages(reindexed)
            if (reindexed.isNotEmpty()) {
                rebuildPdfForDocument(page.documentId, PdfExportOptions())
            }
            Unit
        }
    }

    suspend fun reorderPages(docId: String, orderedPages: List<PageEntity>) = withContext(Dispatchers.IO) {
        val reindexed = orderedPages.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
        pageDao.upsertPages(reindexed)
    }

    suspend fun rebuildPdfForDocument(
        docId: String,
        options: PdfExportOptions
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = documentDao.getDocumentById(docId) ?: error("Document not found")
            val pages = pageDao.getPagesForDocumentOnce(docId)
            val pdfFile = storage.newPdfFile(docId)
            val effectiveTitle = options.title.ifBlank { doc.title }
            pdfEngine.createPdfFromPages(
                pages = pages.map { PageInput(it.processedImagePath, it.ocrText) },
                outputFile = pdfFile,
                options = options.copy(title = effectiveTitle)
            ).getOrThrow()

            if (doc.isEncrypted) {
                security.encryptFileInPlace(pdfFile)
            }
            val updated = doc.copy(
                title = effectiveTitle,
                pageCount = pages.size,
                totalSizeBytes = pdfFile.length(),
                thumbnailPath = pages.firstOrNull()?.thumbnailPath ?: doc.thumbnailPath,
                pdfPath = pdfFile.absolutePath,
                isPdfPasswordProtected = options.password.isNotBlank(),
                updatedAt = System.currentTimeMillis()
            )
            documentDao.updateDocument(updated)
            logActivity(docId, effectiveTitle, "PDF_EXPORTED")
            updated
        }
    }

    suspend fun saveOcrForPage(page: PageEntity, newText: String) = withContext(Dispatchers.IO) {
        pageDao.upsertPage(page.copy(ocrText = newText, updatedAt = System.currentTimeMillis()))
        val allPages = pageDao.getPagesForDocumentOnce(page.documentId)
        val combined = allPages.joinToString("\n\n") { it.ocrText }.trim()
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(ocrSummaryText = combined, updatedAt = System.currentTimeMillis()))
            logActivity(doc.id, doc.title, "OCR_EXTRACTED")
        }
    }

    suspend fun renameDocument(docId: String, newTitle: String) {
        val doc = documentDao.getDocumentById(docId) ?: return
        if (newTitle.isNotBlank()) {
            documentDao.updateDocument(doc.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun toggleFavorite(doc: DocumentEntity) {
        documentDao.updateDocument(doc.copy(isFavorite = !doc.isFavorite))
    }

    suspend fun toggleStarred(doc: DocumentEntity) {
        documentDao.updateDocument(doc.copy(isStarred = !doc.isStarred))
    }

    suspend fun toggleBookmarkPage(docId: String, pageIndex: Int) {
        val doc = documentDao.getDocumentById(docId) ?: return
        val current = doc.bookmarkedPagesCsv.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()
        if (!current.add(pageIndex)) current.remove(pageIndex)
        documentDao.updateDocument(doc.copy(bookmarkedPagesCsv = current.sorted().joinToString(",")))
    }

    suspend fun moveDocuments(ids: List<String>, targetFolderId: String?) {
        documentDao.moveDocumentsToFolder(ids, targetFolderId)
    }

    suspend fun duplicateDocument(docId: String): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val src = documentDao.getDocumentById(docId) ?: error("Document not found")
            val srcPages = pageDao.getPagesForDocumentOnce(docId)
            val newDocId = UUID.randomUUID().toString()
            val newPages = srcPages.mapIndexed { idx, p ->
                val newPageId = UUID.randomUUID().toString()
                val newOrig = storage.newOriginalPageFile(newDocId, newPageId)
                val newProc = storage.newProcessedPageFile(newDocId, newPageId)
                val newThumb = storage.newThumbnailFile(newPageId)
                File(p.originalImagePath).takeIf { it.exists() }?.copyTo(newOrig, overwrite = true)
                File(p.processedImagePath).takeIf { it.exists() }?.copyTo(newProc, overwrite = true)
                File(p.thumbnailPath).takeIf { it.exists() }?.copyTo(newThumb, overwrite = true)
                p.copy(
                    id = newPageId,
                    documentId = newDocId,
                    pageIndex = idx,
                    originalImagePath = newOrig.absolutePath,
                    processedImagePath = newProc.absolutePath,
                    thumbnailPath = newThumb.absolutePath
                )
            }
            val newPdf = storage.newPdfFile(newDocId)
            src.pdfPath?.let { File(it) }?.takeIf { it.exists() }?.copyTo(newPdf, overwrite = true)
            val copyDoc = src.copy(
                id = newDocId,
                title = "${src.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                thumbnailPath = newPages.firstOrNull()?.thumbnailPath,
                pdfPath = newPdf.absolutePath,
                driveFileId = null,
                lastBackedUpAt = null
            )
            documentDao.upsertDocument(copyDoc)
            pageDao.upsertPages(newPages)
            copyDoc
        }
    }

    suspend fun softDelete(ids: List<String>) = documentDao.softDeleteDocuments(ids)
    suspend fun restore(ids: List<String>) = documentDao.restoreDocuments(ids)

    suspend fun hardDelete(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEach { storage.deleteDocumentFiles(it) }
        documentDao.hardDeleteDocuments(ids)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val deleted = documentDao.getDeletedDocumentsOnce()
        hardDelete(deleted.map { it.id })
    }

    suspend fun registerGeneratedPdfDocument(
        title: String,
        pdfFile: File,
        folderId: String? = null,
        isPasswordProtected: Boolean = false
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val destPdf = storage.newPdfFile(docId)
        pdfFile.copyTo(destPdf, overwrite = true)
        val importedImages = pdfEngine.importExternalPdfToImages(Uri.fromFile(destPdf), docId).getOrDefault(emptyList())
        val pages = importedImages.mapIndexed { idx, imgFile ->
            val pageId = UUID.randomUUID().toString()
            val proc = storage.newProcessedPageFile(docId, pageId)
            imgFile.copyTo(proc, overwrite = true)
            val bmp = ImageProcessor.decodeSampledBitmap(proc.absolutePath, 1400)
            val thumb = bmp?.let {
                val t = storage.saveThumbnail(it, pageId)
                val w = it.width; val h = it.height
                it.recycle()
                Triple(t.absolutePath, w, h)
            } ?: Triple(proc.absolutePath, 1000, 1400)
            PageEntity(
                id = pageId,
                documentId = docId,
                pageIndex = idx,
                originalImagePath = imgFile.absolutePath,
                processedImagePath = proc.absolutePath,
                thumbnailPath = thumb.first,
                adjustmentsJson = json.encodeToString(ImageAdjustments()),
                widthPx = thumb.second,
                heightPx = thumb.third
            )
        }
        val doc = DocumentEntity(
            id = docId,
            title = title,
            folderId = folderId,
            pageCount = pages.size.coerceAtLeast(pdfEngine.getPdfPageCount(destPdf)),
            totalSizeBytes = destPdf.length(),
            thumbnailPath = pages.firstOrNull()?.thumbnailPath,
            pdfPath = destPdf.absolutePath,
            isPdfPasswordProtected = isPasswordProtected
        )
        documentDao.upsertDocument(doc)
        pageDao.upsertPages(pages)
        logActivity(docId, title, "PDF_EXPORTED")
        doc
    }

    suspend fun getReadablePdfFile(doc: DocumentEntity): File? = withContext(Dispatchers.IO) {
        val path = doc.pdfPath ?: return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        if (!doc.isEncrypted) return@withContext file
        val temp = storage.newExportCacheFile("dec_${doc.id}.pdf")
        val plain = security.readDecryptedFile(file)
        temp.writeBytes(plain)
        temp
    }

    suspend fun saveSignature(name: String, bitmap: Bitmap): SignatureEntity = withContext(Dispatchers.IO) {
        val file = storage.newSignatureFile()
        storage.saveBitmapAtomic(bitmap, file, Bitmap.CompressFormat.PNG, 100)
        val sig = SignatureEntity(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Signature" },
            imagePath = file.absolutePath
        )
        signatureDao.upsertSignature(sig)
        sig
    }

    suspend fun deleteSignature(sig: SignatureEntity) = withContext(Dispatchers.IO) {
        storage.secureDeleteFile(File(sig.imagePath))
        signatureDao.deleteSignature(sig.id)
    }

    suspend fun logBackupHistory(status: String, count: Int, bytes: Long, msg: String) {
        backupHistoryDao.insertHistory(
            BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                status = status,
                uploadedCount = count,
                totalBytes = bytes,
                message = msg
            )
        )
    }

    suspend fun logActivity(docId: String?, title: String, actionType: String) {
        activityLogDao.insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                documentId = docId,
                documentTitle = title,
                actionType = actionType
            )
        )
    }
}
