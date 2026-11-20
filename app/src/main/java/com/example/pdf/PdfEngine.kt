package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.R
import com.example.core.FileStorageManager
import com.example.data.model.AnnotationKind
import com.example.data.model.PdfAnnotationItem
import com.example.data.model.PdfExportOptions
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

data class PageInput(
    val imagePath: String,
    val ocrText: String = ""
)

class PdfEngine(
    private val context: Context,
    private val storage: FileStorageManager
) {
    private fun mmToPt(mm: Float): Float = mm * 72f / 25.4f

    private fun resolvePageRect(options: PdfExportOptions): PDRectangle {
        val baseW: Float
        val baseH: Float
        if (options.pageSize == PdfPageSize.CUSTOM) {
            baseW = mmToPt(options.customWidthMm.coerceIn(50f, 1200f))
            baseH = mmToPt(options.customHeightMm.coerceIn(50f, 1600f))
        } else {
            baseW = options.pageSize.widthPt
            baseH = options.pageSize.heightPt
        }
        return if (options.orientation == PdfOrientation.LANDSCAPE) {
            PDRectangle(maxOf(baseW, baseH), minOf(baseW, baseH))
        } else {
            PDRectangle(minOf(baseW, baseH), maxOf(baseW, baseH))
        }
    }

    private fun loadSafeFont(doc: PDDocument, text: String): PDFont {
        val needsBengali = text.any { it.code in 0x0980..0x09FF }
        val nonAscii = text.any { it.code > 127 }
        return try {
            when {
                needsBengali -> context.resources.openRawResource(R.font.noto_sans_bengali).use {
                    PDType0Font.load(doc, it, true)
                }
                nonAscii -> context.resources.openRawResource(R.font.plus_jakarta_sans).use {
                    PDType0Font.load(doc, it, true)
                }
                else -> PDType1Font.HELVETICA
            }
        } catch (_: Exception) {
            PDType1Font.HELVETICA
        }
    }

    private fun safeEncodeText(font: PDFont, raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ')
                continue
            }
            val s = ch.toString()
            val ok = runCatching { font.encode(s) }.isSuccess
            if (ok) sb.append(ch)
        }
        return sb.toString()
    }

    suspend fun createPdfFromPages(
        pages: List<PageInput>,
        outputFile: File,
        options: PdfExportOptions,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "At least one page is required" }
            outputFile.parentFile?.mkdirs()
            val tmpOut = File(outputFile.parentFile, "${outputFile.name}.tmp")

            PDDocument(MemoryUsageSetting.setupMixed(24 * 1024 * 1024)).use { doc ->
                val info = doc.documentInformation
                info.title = options.title.ifBlank { "Scanned Document" }
                info.author = options.author.ifBlank { "ScanPro PDF" }
                info.creator = "ScanPro PDF"

                val pageRect = resolvePageRect(options)
                val marginPt = mmToPt(options.marginMm.coerceIn(0f, 40f))
                val qualityFrac = (options.quality.coerceIn(30, 100) / 100f)

                pages.forEachIndexed { idx, pageInput ->
                    onProgress(idx + 1, pages.size)
                    val page = PDPage(pageRect)
                    doc.addPage(page)

                    val bmp = BitmapFactory.decodeFile(pageInput.imagePath)
                        ?: error("Invalid image at page ${idx + 1}")

                    try {
                        val pdImage = JPEGFactory.createFromImage(doc, bmp, qualityFrac)
                        val availW = (pageRect.width - 2f * marginPt).coerceAtLeast(32f)
                        val availH = (pageRect.height - 2f * marginPt).coerceAtLeast(32f)
                        val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val drawX = marginPt + (availW - drawW) / 2f
                        val drawY = marginPt + (availH - drawH) / 2f

                        PDPageContentStream(doc, page).use { cs ->
                            // Optional invisible searchable OCR layer beneath the image
                            if (options.searchableOcr && pageInput.ocrText.isNotBlank()) {
                                val ocrFont = loadSafeFont(doc, pageInput.ocrText)
                                val lines = pageInput.ocrText.lines().filter { it.isNotBlank() }.take(80)
                                val stepY = (drawH / (lines.size + 1).coerceAtLeast(1)).coerceIn(10f, 18f)
                                cs.beginText()
                                cs.setFont(ocrFont, 9f)
                                cs.setRenderingMode(RenderingMode.NEITHER)
                                lines.forEachIndexed { lineIdx, line ->
                                    val clean = safeEncodeText(ocrFont, line)
                                    if (clean.isNotBlank()) {
                                        val yPos = (drawY + drawH - ((lineIdx + 1) * stepY)).coerceAtLeast(drawY + 4f)
                                        cs.setTextMatrix(PdfMatrix.getTranslateInstance(drawX + 6f, yPos))
                                        cs.showText(clean)
                                    }
                                }
                                cs.endText()
                                cs.setRenderingMode(RenderingMode.FILL)
                            }

                            // Draw scanned page image
                            cs.drawImage(pdImage, drawX, drawY, drawW, drawH)

                            // Optional Watermark
                            if (options.watermarkText.isNotBlank()) {
                                val wmFont = loadSafeFont(doc, options.watermarkText)
                                val cleanWm = safeEncodeText(wmFont, options.watermarkText)
                                if (cleanWm.isNotBlank()) {
                                    val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.20f }
                                    cs.setGraphicsStateParameters(gs)
                                    cs.setNonStrokingColor(100, 116, 139)
                                    cs.beginText()
                                    cs.setFont(wmFont, 38f)
                                    val matrix = PdfMatrix.getRotateInstance(
                                        Math.toRadians(35.0),
                                        pageRect.width * 0.22f,
                                        pageRect.height * 0.36f
                                    )
                                    cs.setTextMatrix(matrix)
                                    cs.showText(cleanWm)
                                    cs.endText()
                                }
                            }

                            // Optional Page Numbering
                            if (options.includePageNumbers) {
                                val gsFull = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.85f }
                                cs.setGraphicsStateParameters(gsFull)
                                cs.setNonStrokingColor(71, 85, 105)
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 9f)
                                cs.newLineAtOffset(pageRect.width / 2f - 18f, 14f)
                                cs.showText("${idx + 1} / ${pages.size}")
                                cs.endText()
                            }
                        }
                    } finally {
                        bmp.recycle()
                    }
                }

                if (options.password.isNotBlank()) {
                    val ap = AccessPermission()
                    val spp = StandardProtectionPolicy(options.password, options.password, ap).apply {
                        encryptionKeyLength = 128
                        permissions = ap
                    }
                    doc.protect(spp)
                }

                doc.save(tmpOut)
            }

            if (outputFile.exists()) outputFile.delete()
            if (!tmpOut.renameTo(outputFile)) {
                tmpOut.copyTo(outputFile, overwrite = true)
                tmpOut.delete()
            }
            outputFile
        }
    }

    suspend fun mergePdfs(inputFiles: List<File>, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.size >= 2) { "Select at least two PDFs to merge" }
            outputFile.parentFile?.mkdirs()
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            inputFiles.forEach { file ->
                if (file.exists()) merger.addSource(file)
            }
            merger.mergeDocuments(MemoryUsageSetting.setupMixed(24 * 1024 * 1024))
            outputFile
        }
    }

    suspend fun splitPdfByRange(
        inputFile: File,
        rangeSpec: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile).use { src ->
                val indices = parsePageRange(rangeSpec, src.numberOfPages)
                require(indices.isNotEmpty()) { "No valid pages selected" }
                PDDocument().use { dst ->
                    indices.forEach { pageIdx ->
                        dst.importPage(src.getPage(pageIdx))
                    }
                    dst.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun transformPdfPages(
        inputFile: File,
        orderedPageIndicesAndRotations: List<Pair<Int, Int>>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile).use { src ->
                PDDocument().use { dst ->
                    orderedPageIndicesAndRotations.forEach { (idx, extraRotation) ->
                        if (idx in 0 until src.numberOfPages) {
                            val imported = dst.importPage(src.getPage(idx))
                            imported.rotation = (imported.rotation + extraRotation) % 360
                        }
                    }
                    dst.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun compressPdf(
        inputFile: File,
        quality: Int,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val scaleFactor = if (quality >= 75) 1.5f else if (quality >= 50) 1.25f else 1.0f
            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    PDDocument(MemoryUsageSetting.setupMixed(24 * 1024 * 1024)).use { outDoc ->
                        val qFrac = (quality.coerceIn(25, 95) / 100f)
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val w = (page.width * scaleFactor).toInt().coerceAtLeast(300)
                                val h = (page.height * scaleFactor).toInt().coerceAtLeast(300)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val rect = PDRectangle(page.width.toFloat(), page.height.toFloat())
                                val pdPage = PDPage(rect)
                                outDoc.addPage(pdPage)
                                val img = JPEGFactory.createFromImage(outDoc, bmp, qFrac)
                                bmp.recycle()
                                PDPageContentStream(outDoc, pdPage).use { cs ->
                                    cs.drawImage(img, 0f, 0f, rect.width, rect.height)
                                }
                            }
                        }
                        outDoc.save(outputFile)
                    }
                }
            }
            outputFile
        }
    }

    suspend fun applyWatermarkAndEncryption(
        inputFile: File,
        watermarkText: String,
        includePageNumbers: Boolean,
        password: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile).use { doc ->
                val total = doc.numberOfPages
                val wmFont = if (watermarkText.isNotBlank()) loadSafeFont(doc, watermarkText) else PDType1Font.HELVETICA
                val cleanWm = if (watermarkText.isNotBlank()) safeEncodeText(wmFont, watermarkText) else ""

                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        if (cleanWm.isNotBlank()) {
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.22f }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(100, 116, 139)
                            cs.beginText()
                            cs.setFont(wmFont, 38f)
                            cs.setTextMatrix(
                                PdfMatrix.getRotateInstance(
                                    Math.toRadians(35.0),
                                    box.width * 0.22f,
                                    box.height * 0.36f
                                )
                            )
                            cs.showText(cleanWm)
                            cs.endText()
                        }
                        if (includePageNumbers) {
                            val gsFull = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.85f }
                            cs.setGraphicsStateParameters(gsFull)
                            cs.setNonStrokingColor(71, 85, 105)
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, 9f)
                            cs.newLineAtOffset(box.width / 2f - 18f, 14f)
                            cs.showText("${i + 1} / $total")
                            cs.endText()
                        }
                    }
                }

                if (password.isNotBlank()) {
                    val ap = AccessPermission()
                    val spp = StandardProtectionPolicy(password, password, ap).apply {
                        encryptionKeyLength = 128
                    }
                    doc.protect(spp)
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun flattenAnnotationsToPdf(
        inputFile: File,
        annotations: List<PdfAnnotationItem>,
        outputFile: File,
        password: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val tmpOut = File(outputFile.parentFile, "${outputFile.name}.ann.tmp")
            val doc = if (password.isNotBlank()) PDDocument.load(inputFile, password) else PDDocument.load(inputFile)
            doc.use { pdDoc ->
                val byPage = annotations.groupBy { it.pageIndex }
                byPage.forEach { (pageIdx, items) ->
                    if (pageIdx in 0 until pdDoc.numberOfPages) {
                        val page = pdDoc.getPage(pageIdx)
                        val box = page.mediaBox
                        val w = box.width
                        val h = box.height

                        PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            items.forEach { item ->
                                val r = ((item.colorArgb shr 16) and 0xFF).toInt()
                                val g = ((item.colorArgb shr 8) and 0xFF).toInt()
                                val b = (item.colorArgb and 0xFF).toInt()

                                when (item.kind) {
                                    AnnotationKind.HIGHLIGHT -> {
                                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setNonStrokingColor(r, g, b)
                                        cs.addRect(item.x * w, h - (item.y + item.height) * h, item.width * w, item.height * h)
                                        cs.fill()
                                    }
                                    AnnotationKind.UNDERLINE -> {
                                        val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.95f }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setStrokingColor(r, g, b)
                                        cs.setLineWidth(2.2f)
                                        val yPt = h - (item.y + item.height) * h
                                        cs.moveTo(item.x * w, yPt)
                                        cs.lineTo((item.x + item.width) * w, yPt)
                                        cs.stroke()
                                    }
                                    AnnotationKind.STRIKETHROUGH -> {
                                        val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.95f }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setStrokingColor(r, g, b)
                                        cs.setLineWidth(2.0f)
                                        val yPt = h - (item.y + item.height * 0.5f) * h
                                        cs.moveTo(item.x * w, yPt)
                                        cs.lineTo((item.x + item.width) * w, yPt)
                                        cs.stroke()
                                    }
                                    AnnotationKind.FREEHAND -> {
                                        if (item.points.size >= 2) {
                                            val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1.0f }
                                            cs.setGraphicsStateParameters(gs)
                                            cs.setStrokingColor(r, g, b)
                                            cs.setLineWidth((item.strokeWidthNorm * w).coerceIn(1.2f, 12f))
                                            val first = item.points.first()
                                            cs.moveTo(first.x * w, h - first.y * h)
                                            for (i in 1 until item.points.size) {
                                                val p = item.points[i]
                                                cs.lineTo(p.x * w, h - p.y * h)
                                            }
                                            cs.stroke()
                                        }
                                    }
                                    AnnotationKind.SHAPE_RECT -> {
                                        val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1.0f }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setStrokingColor(r, g, b)
                                        cs.setLineWidth(2.2f)
                                        cs.addRect(item.x * w, h - (item.y + item.height) * h, item.width * w, item.height * h)
                                        cs.stroke()
                                    }
                                    AnnotationKind.TEXT_BOX, AnnotationKind.STICKY_NOTE -> {
                                        val gsBg = PDExtendedGraphicsState().apply {
                                            nonStrokingAlphaConstant = if (item.kind == AnnotationKind.STICKY_NOTE) 0.9f else 0.0f
                                        }
                                        if (item.kind == AnnotationKind.STICKY_NOTE) {
                                            cs.setGraphicsStateParameters(gsBg)
                                            cs.setNonStrokingColor(254, 240, 138)
                                            cs.addRect(item.x * w, h - (item.y + item.height) * h, item.width * w, item.height * h)
                                            cs.fill()
                                        }
                                        val gsTxt = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1.0f }
                                        cs.setGraphicsStateParameters(gsTxt)
                                        cs.setNonStrokingColor(r, g, b)
                                        val font = loadSafeFont(pdDoc, item.text)
                                        val clean = safeEncodeText(font, item.text)
                                        if (clean.isNotBlank()) {
                                            cs.beginText()
                                            cs.setFont(font, 12f)
                                            cs.newLineAtOffset(item.x * w + 4f, h - item.y * h - 14f)
                                            cs.showText(clean)
                                            cs.endText()
                                        }
                                    }
                                    AnnotationKind.SIGNATURE -> {
                                        val sigBmp = BitmapFactory.decodeFile(item.signaturePath)
                                        if (sigBmp != null) {
                                            val rotBmp = if (item.rotationDeg != 0f) {
                                                val m = Matrix().apply { postRotate(item.rotationDeg) }
                                                Bitmap.createBitmap(sigBmp, 0, 0, sigBmp.width, sigBmp.height, m, true).also {
                                                    sigBmp.recycle()
                                                }
                                            } else sigBmp
                                            val pdSig = LosslessFactory.createFromImage(pdDoc, rotBmp)
                                            rotBmp.recycle()
                                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1.0f }
                                            cs.setGraphicsStateParameters(gs)
                                            cs.drawImage(
                                                pdSig,
                                                item.x * w,
                                                h - (item.y + item.height) * h,
                                                item.width * w,
                                                item.height * h
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                pdDoc.save(tmpOut)
            }
            if (outputFile.exists()) outputFile.delete()
            if (!tmpOut.renameTo(outputFile)) {
                tmpOut.copyTo(outputFile, overwrite = true)
                tmpOut.delete()
            }
            outputFile
        }
    }

    suspend fun exportPdfToImages(
        pdfFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        baseName: String
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val out = mutableListOf<File>()
            val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val f = storage.newExportCacheFile("${baseName}_page_${i + 1}.$ext")
                            storage.saveBitmapAtomic(bmp, f, format, 94)
                            bmp.recycle()
                            out.add(f)
                        }
                    }
                }
            }
            out
        }
    }

    suspend fun importExternalPdfToImages(uri: Uri, docId: String): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val tmpPdf = storage.newExportCacheFile("import_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpPdf).use { output -> input.copyTo(output) }
            } ?: error("Cannot read selected PDF")

            val resultFiles = mutableListOf<File>()
            val pfd = ParcelFileDescriptor.open(tmpPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = 2.0f
                            val w = (page.width * scale).toInt().coerceIn(600, 2200)
                            val h = (page.height * scale).toInt().coerceIn(600, 3000)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val pageId = "p_${i}_${System.currentTimeMillis()}"
                            val origFile = storage.newOriginalPageFile(docId, pageId)
                            storage.saveBitmapAtomic(bmp, origFile, Bitmap.CompressFormat.JPEG, 92)
                            bmp.recycle()
                            resultFiles.add(origFile)
                        }
                    }
                }
            }
            storage.secureDeleteFile(tmpPdf)
            resultFiles
        }
    }

    fun getPdfPageCount(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
        }.getOrDefault(0)
    }

    private fun parsePageRange(spec: String, totalPages: Int): List<Int> {
        val result = linkedSetOf<Int>()
        spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if ("-" in part) {
                val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = bounds[0].coerceIn(1, totalPages)
                    val end = bounds[1].coerceIn(1, totalPages)
                    if (start <= end) {
                        for (p in start..end) result.add(p - 1)
                    }
                }
            } else {
                part.toIntOrNull()?.let { p ->
                    if (p in 1..totalPages) result.add(p - 1)
                }
            }
        }
        return result.toList()
    }
}
