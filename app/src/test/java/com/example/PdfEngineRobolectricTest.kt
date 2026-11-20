package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.core.FileStorageManager
import com.example.data.model.PdfExportOptions
import com.example.data.model.PdfPageSize
import com.example.pdf.PageInput
import com.example.pdf.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfEngineRobolectricTest {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager
    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        storage = FileStorageManager(context)
        pdfEngine = PdfEngine(context, storage)
    }

    @Test
    fun testCreateMultipagePdfWithBengaliWatermarkAndPassword() = runTest {
        val imgFiles = (1..3).map { idx ->
            val f = File(context.cacheDir, "test_page_$idx.jpg")
            val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            storage.saveBitmapAtomic(bmp, f, Bitmap.CompressFormat.JPEG, 85)
            bmp.recycle()
            f
        }

        val outPdf = File(context.cacheDir, "protected_test.pdf")
        val res = pdfEngine.createPdfFromPages(
            pages = imgFiles.mapIndexed { i, f -> PageInput(f.absolutePath, "নমুনা বাংলা লেখা $i Sample English") },
            outputFile = outPdf,
            options = PdfExportOptions(
                title = "Test Bengali PDF",
                author = "ScanPro",
                pageSize = PdfPageSize.A4,
                password = "secret",
                watermarkText = "গোপনীয়",
                includePageNumbers = true,
                searchableOcr = true
            )
        )

        assertTrue(res.isSuccess)
        assertTrue(outPdf.exists() && outPdf.length() > 500)

        PDDocument.load(outPdf, "secret").use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertTrue(doc.isEncrypted)
        }
    }

    @Test
    fun testMergeSplitTransformAndAnnotatePdf() = runTest {
        val img = File(context.cacheDir, "ann_page.jpg")
        val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        storage.saveBitmapAtomic(bmp, img, Bitmap.CompressFormat.JPEG, 90)
        bmp.recycle()

        val pdfA = File(context.cacheDir, "docA.pdf")
        val pdfB = File(context.cacheDir, "docB.pdf")
        pdfEngine.createPdfFromPages(listOf(PageInput(img.absolutePath)), pdfA, PdfExportOptions(title = "Doc A")).getOrThrow()
        pdfEngine.createPdfFromPages(listOf(PageInput(img.absolutePath), PageInput(img.absolutePath)), pdfB, PdfExportOptions(title = "Doc B")).getOrThrow()

        // Merge A (1p) + B (2p) -> 3p
        val merged = File(context.cacheDir, "merged.pdf")
        pdfEngine.mergePdfs(listOf(pdfA, pdfB), merged).getOrThrow()
        PDDocument.load(merged).use { assertEquals(3, it.numberOfPages) }

        // Split pages 2-3 -> 2p
        val split = File(context.cacheDir, "split.pdf")
        pdfEngine.splitPdfByRange(merged, "2-3", split).getOrThrow()
        PDDocument.load(split).use { assertEquals(2, it.numberOfPages) }

        // Reorder & rotate 90 deg
        val transformed = File(context.cacheDir, "transformed.pdf")
        pdfEngine.transformPdfPages(split, listOf(1 to 90, 0 to 0), transformed).getOrThrow()
        PDDocument.load(transformed).use {
            assertEquals(2, it.numberOfPages)
            assertEquals(90, it.getPage(0).rotation)
        }

        // Flatten highlight + Bengali text annotation
        val annotated = File(context.cacheDir, "annotated.pdf")
        val anns = listOf(
            com.example.data.model.PdfAnnotationItem(
                id = "a1",
                pageIndex = 0,
                kind = com.example.data.model.AnnotationKind.HIGHLIGHT,
                x = 0.1f, y = 0.1f, width = 0.4f, height = 0.08f
            ),
            com.example.data.model.PdfAnnotationItem(
                id = "a2",
                pageIndex = 0,
                kind = com.example.data.model.AnnotationKind.TEXT_BOX,
                x = 0.1f, y = 0.3f, width = 0.5f, height = 0.1f,
                text = "স্বাক্ষরিত নথি Verified"
            )
        )
        val annRes = pdfEngine.flattenAnnotationsToPdf(transformed, anns, annotated)
        assertTrue(annRes.isSuccess)
        PDDocument.load(annotated).use { assertEquals(2, it.numberOfPages) }
    }
}
