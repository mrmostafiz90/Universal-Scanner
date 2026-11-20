package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.FileStorageManager
import com.example.data.local.ScanProDatabase
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import com.example.data.model.PdfExportOptions
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.DocumentRepository
import com.example.localization.AppLanguage
import com.example.pdf.PdfEngine
import com.example.security.SecurityManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: ScanProDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var repo: DocumentRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        db = Room.inMemoryDatabaseBuilder(context, ScanProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = FileStorageManager(context)
        val pdfEngine = PdfEngine(context, storage)
        val security = SecurityManager()
        repo = DocumentRepository(
            context = context,
            documentDao = db.documentDao(),
            folderDao = db.folderDao(),
            pageDao = db.pageDao(),
            signatureDao = db.signatureDao(),
            backupHistoryDao = db.backupHistoryDao(),
            activityLogDao = db.activityLogDao(),
            storage = storage,
            pdfEngine = pdfEngine,
            security = security
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testFolderDocumentLifecycleAndOcrSearch() = runTest {
        // Create & rename folder
        val folder = repo.createFolder("Invoices")
        repo.renameFolder(folder.id, "Tax Invoices")
        val folders = repo.allFolders.first()
        assertEquals(1, folders.size)
        assertEquals("Tax Invoices", folders.first().name)

        // Create scanned image file
        val srcFile = File(context.cacheDir, "repo_scan_1.jpg")
        val bmp = Bitmap.createBitmap(220, 300, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        storage.saveBitmapAtomic(bmp, srcFile, Bitmap.CompressFormat.JPEG, 90)
        bmp.recycle()

        // Create document
        val doc = repo.createDocumentFromCapturedImages(
            title = "Utility Bill",
            sourceFiles = listOf(srcFile),
            defaultAdjustments = ImageAdjustments(filterType = FilterType.DOCUMENT_CLEAR),
            autoCrop = false,
            exportOptions = PdfExportOptions(title = "Utility Bill"),
            folderId = folder.id,
            encryptLocal = false
        ).getOrThrow()

        assertEquals(1, doc.pageCount)
        assertNotNull(doc.pdfPath)
        assertTrue(File(doc.pdfPath!!).exists())

        // Save OCR text and verify summary update
        val pages = repo.getPagesOnce(doc.id)
        assertEquals(1, pages.size)
        repo.saveOcrForPage(pages.first(), "বিদ্যুৎ বিল Invoice 2025")
        val reloaded = repo.getDocumentById(doc.id)!!
        assertTrue(reloaded.ocrSummaryText.contains("বিদ্যুৎ বিল"))

        // Favorite, Star, Soft Delete & Restore
        repo.toggleFavorite(reloaded)
        repo.toggleStarred(reloaded)
        repo.softDelete(listOf(doc.id))
        assertEquals(0, repo.activeDocuments.first().size)
        assertEquals(1, repo.deletedDocuments.first().size)

        repo.restore(listOf(doc.id))
        assertEquals(1, repo.activeDocuments.first().size)
        assertEquals(0, repo.deletedDocuments.first().size)
    }

    @Test
    fun testSettingsExportAndImportRoundTrip() = runTest {
        val settingsRepo = SettingsRepository(context)
        settingsRepo.setLanguage(AppLanguage.BENGALI)
        settingsRepo.setDefaultQuality(90)
        settingsRepo.setAutoCrop(false)

        val snapshotJson = settingsRepo.exportSettingsJson()
        settingsRepo.setLanguage(AppLanguage.ENGLISH)
        settingsRepo.setDefaultQuality(60)

        assertTrue(settingsRepo.importSettingsJson(snapshotJson))
        val restored = settingsRepo.settingsFlow.first()
        assertEquals(AppLanguage.BENGALI, restored.language)
        assertEquals(90, restored.defaultQuality)
        assertEquals(false, restored.autoCrop)
    }
}
