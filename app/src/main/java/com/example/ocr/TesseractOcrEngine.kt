package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.core.FileStorageManager
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import com.example.data.model.OcrLanguage
import com.example.data.model.OcrModelStatus
import com.example.scanner.ImageProcessor
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class TesseractOcrEngine(
    private val context: Context,
    private val storage: FileStorageManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val downloadableLanguages = listOf(
        Triple("eng", "English (eng)", true),
        Triple("ben", "Bengali / বাংলা (ben)", true),
        Triple("hin", "Hindi / हिन्दी (hin)", false),
        Triple("ara", "Arabic / العربية (ara)", false)
    )

    suspend fun ensureBundledModelsExtracted() = withContext(Dispatchers.IO) {
        val dir = storage.tessdataDir
        if (!dir.exists()) dir.mkdirs()

        listOf("eng.traineddata", "ben.traineddata").forEach { fileName ->
            val target = File(dir, fileName)
            if (!target.exists() || target.length() < 10_000L) {
                runCatching {
                    context.assets.open("tessdata/$fileName").use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    suspend fun getModelStatuses(): List<OcrModelStatus> = withContext(Dispatchers.IO) {
        ensureBundledModelsExtracted()
        downloadableLanguages.map { (code, name, bundled) ->
            val file = File(storage.tessdataDir, "$code.traineddata")
            OcrModelStatus(
                code = code,
                displayName = name,
                isInstalled = file.exists() && file.length() > 10_000L,
                sizeBytes = if (file.exists()) file.length() else 0L,
                isBundled = bundled
            )
        }
    }

    suspend fun downloadLanguageModel(
        code: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$code.traineddata"
            val req = Request.Builder().url(url).build()
            val target = File(storage.tessdataDir, "$code.traineddata")
            val tmp = File(storage.tessdataDir, "$code.traineddata.tmp")
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Empty response")
                val total = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buf = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            if (!isActive) error("Cancelled")
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            copied += read
                            onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            Unit
        }
    }

    suspend fun deleteLanguageModel(code: String) = withContext(Dispatchers.IO) {
        if (code == "eng" || code == "ben") return@withContext // Keep core offline models safe
        File(storage.tessdataDir, "$code.traineddata").delete()
    }

    suspend fun recognizeTextFromFile(
        imagePath: String,
        language: OcrLanguage
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            ensureBundledModelsExtracted()
            val requiredCodes = language.tessCode.split("+")
            for (code in requiredCodes) {
                val modelFile = File(storage.tessdataDir, "$code.traineddata")
                if (!modelFile.exists() || modelFile.length() < 10_000L) {
                    error("OCR language pack '$code' is not downloaded.")
                }
            }

            val raw = ImageProcessor.decodeSampledBitmap(imagePath, maxLongEdge = 2000)
                ?: error("Could not decode image for OCR")

            // Pre-enhance lightly if image is dark for higher Bengali/English recognition accuracy
            val prepped = ImageProcessor.applyAdjustments(
                raw,
                ImageAdjustments(filterType = FilterType.DOCUMENT_CLEAR, intensity = 0.7f, sharpness = 0.35f),
                maxOutputLongEdge = 2000
            )
            if (prepped !== raw) raw.recycle()

            val recognized = runTessApi(prepped, language.tessCode)
            prepped.recycle()
            recognized.trim()
        }
    }

    private fun runTessApi(bitmap: Bitmap, tessCode: String): String {
        val tess = TessBaseAPI()
        return try {
            val initialized = tess.init(context.filesDir.absolutePath, tessCode)
            if (!initialized) {
                error("Failed to initialize Tesseract for $tessCode")
            }
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)
            tess.utF8Text ?: ""
        } finally {
            tess.recycle()
        }
    }
}
