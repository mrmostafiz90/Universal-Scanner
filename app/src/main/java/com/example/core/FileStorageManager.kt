package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID

class FileStorageManager(private val context: Context) {

    val rootDocsDir: File by lazy {
        File(context.filesDir, "documents").apply { mkdirs() }
    }
    val pagesDir: File by lazy {
        File(context.filesDir, "pages").apply { mkdirs() }
    }
    val thumbnailsDir: File by lazy {
        File(context.filesDir, "thumbnails").apply { mkdirs() }
    }
    val signaturesDir: File by lazy {
        File(context.filesDir, "signatures").apply { mkdirs() }
    }
    val exportsCacheDir: File by lazy {
        File(context.cacheDir, "exports").apply { mkdirs() }
    }
    val cameraTempDir: File by lazy {
        File(context.cacheDir, "camera").apply { mkdirs() }
    }
    val tessdataDir: File by lazy {
        File(context.filesDir, "tessdata").apply { mkdirs() }
    }

    fun newOriginalPageFile(docId: String, pageId: String): File {
        val dir = File(pagesDir, docId).apply { mkdirs() }
        return File(dir, "${pageId}_orig.jpg")
    }

    fun newProcessedPageFile(docId: String, pageId: String): File {
        val dir = File(pagesDir, docId).apply { mkdirs() }
        return File(dir, "${pageId}_proc.jpg")
    }

    fun newThumbnailFile(id: String): File = File(thumbnailsDir, "${id}_thumb.jpg")

    fun newPdfFile(docId: String): File = File(rootDocsDir, "$docId.pdf")

    fun newSignatureFile(): File = File(signaturesDir, "sig_${UUID.randomUUID()}.png")

    fun newTempCameraFile(): File = File(cameraTempDir, "scan_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

    fun newExportCacheFile(fileName: String): File {
        val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF ]"), "_").trim()
        return File(exportsCacheDir, sanitized.ifBlank { "export_${System.currentTimeMillis()}" })
    }

    fun saveBitmapAtomic(bitmap: Bitmap, dest: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 92): File {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(tmp).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        return dest
    }

    fun saveThumbnail(bitmap: Bitmap, id: String): File {
        val maxDim = 360
        val ratio = minOf(maxDim.toFloat() / bitmap.width.coerceAtLeast(1), maxDim.toFloat() / bitmap.height.coerceAtLeast(1), 1f)
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val target = newThumbnailFile(id)
        saveBitmapAtomic(scaled, target, Bitmap.CompressFormat.JPEG, 78)
        if (scaled !== bitmap) scaled.recycle()
        return target
    }

    fun calculateUsedStorageBytes(): Long {
        return folderSize(rootDocsDir) + folderSize(pagesDir) + folderSize(thumbnailsDir) + folderSize(signaturesDir)
    }

    fun calculateTempCacheBytes(): Long {
        return folderSize(exportsCacheDir) + folderSize(cameraTempDir)
    }

    fun hasSufficientStorage(minBytesRequired: Long = 20L * 1024L * 1024L): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes >= minBytesRequired
        } catch (_: Exception) {
            true
        }
    }

    fun securelyWipeTempFiles() {
        secureDeleteTree(exportsCacheDir)
        secureDeleteTree(cameraTempDir)
        exportsCacheDir.mkdirs()
        cameraTempDir.mkdirs()
    }

    fun secureDeleteFile(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isFile && file.length() in 1..(15 * 1024 * 1024)) {
                val random = SecureRandom()
                val junk = ByteArray(minOf(file.length().toInt(), 4096))
                random.nextBytes(junk)
                FileOutputStream(file).use { it.write(junk) }
            }
        } catch (_: Exception) {
        } finally {
            file.delete()
        }
    }

    fun deleteDocumentFiles(docId: String) {
        secureDeleteFile(newPdfFile(docId))
        secureDeleteFile(newThumbnailFile(docId))
        val pageFolder = File(pagesDir, docId)
        if (pageFolder.exists()) {
            secureDeleteTree(pageFolder)
        }
    }

    private fun secureDeleteTree(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) secureDeleteTree(child) else secureDeleteFile(child)
        }
        dir.delete()
    }

    private fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.listFiles()?.forEach { file ->
            sum += if (file.isDirectory) folderSize(file) else file.length()
        }
        return sum
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}
