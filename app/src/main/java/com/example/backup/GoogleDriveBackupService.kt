package com.example.backup

import android.content.Context
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.DocumentRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class DriveBackupManifest(
    val exportedAt: Long,
    val folders: List<DriveFolderRecord>,
    val documents: List<DriveDocRecord>
)

@Serializable
data class DriveFolderRecord(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val colorHex: Long
)

@Serializable
data class DriveDocRecord(
    val id: String,
    val title: String,
    val folderId: String?,
    val pageCount: Int,
    val ocrSummaryText: String,
    val updatedAt: Long,
    val driveFileId: String? = null
)

class GoogleDriveBackupService(
    private val context: Context,
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_FOLDER_NAME = "ScanPro PDF Backups"
    }

    suspend fun requestDriveAuthorization(): AuthorizationResult {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_FILE_SCOPE),
                    Scope("email"),
                    Scope("profile")
                )
            )
            .build()
        return Identity.getAuthorizationClient(context).authorize(authRequest).await()
    }

    suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JSONObject(body).optString("email").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    suspend fun backupDocuments(
        accessToken: String,
        selectedFolderIds: Set<String>? = null,
        includeSettings: Boolean = true,
        onProgress: (Int, Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = getOrCreateBackupFolder(accessToken)
            val allDocs = repository.activeDocuments.first()
            val allFolders = repository.allFolders.first()
            val docsToSync = if (selectedFolderIds.isNullOrEmpty()) {
                allDocs
            } else {
                allDocs.filter { it.folderId in selectedFolderIds }
            }

            val totalSteps = docsToSync.size + 1
            var done = 0
            var totalBytes = 0L
            val docRecords = mutableListOf<DriveDocRecord>()

            docsToSync.forEach { doc ->
                val pdfFile = repository.getReadablePdfFile(doc)
                var remoteId = doc.driveFileId
                if (pdfFile != null && pdfFile.exists()) {
                    // Duplicate detection: check existing remote file with same localDocId or name
                    if (remoteId.isNullOrBlank()) {
                        remoteId = findExistingFileInFolder(accessToken, folderId, "${doc.id}.pdf")
                    }
                    remoteId = uploadOrUpdateFile(
                        accessToken = accessToken,
                        parentFolderId = folderId,
                        existingFileId = remoteId,
                        fileName = "${doc.id}.pdf",
                        mimeType = "application/pdf",
                        file = pdfFile
                    )
                    totalBytes += pdfFile.length()
                    if (doc.isEncrypted) repository.storage.secureDeleteFile(pdfFile)
                }
                docRecords.add(
                    DriveDocRecord(
                        id = doc.id,
                        title = doc.title,
                        folderId = doc.folderId,
                        pageCount = doc.pageCount,
                        ocrSummaryText = doc.ocrSummaryText,
                        updatedAt = doc.updatedAt,
                        driveFileId = remoteId
                    )
                )
                done++
                onProgress(done, totalSteps)
            }

            // Upload manifest JSON
            val manifest = DriveBackupManifest(
                exportedAt = System.currentTimeMillis(),
                folders = allFolders.map { DriveFolderRecord(it.id, it.name, it.parentFolderId, it.colorHex) },
                documents = docRecords
            )
            val manifestFile = repository.storage.newExportCacheFile("scanpro_manifest.json")
            manifestFile.writeText(json.encodeToString(manifest))
            val existingManifestId = findExistingFileInFolder(accessToken, folderId, "scanpro_manifest.json")
            uploadOrUpdateFile(
                accessToken = accessToken,
                parentFolderId = folderId,
                existingFileId = existingManifestId,
                fileName = "scanpro_manifest.json",
                mimeType = "application/json",
                file = manifestFile
            )
            repository.storage.secureDeleteFile(manifestFile)

            if (includeSettings) {
                val settingsFile = repository.storage.newExportCacheFile("scanpro_settings.json")
                settingsFile.writeText(settingsRepository.exportSettingsJson())
                val existingSettingsId = findExistingFileInFolder(accessToken, folderId, "scanpro_settings.json")
                uploadOrUpdateFile(
                    accessToken = accessToken,
                    parentFolderId = folderId,
                    existingFileId = existingSettingsId,
                    fileName = "scanpro_settings.json",
                    mimeType = "application/json",
                    file = settingsFile
                )
                repository.storage.secureDeleteFile(settingsFile)
            }

            done++
            onProgress(done, totalSteps)

            val now = System.currentTimeMillis()
            settingsRepository.setLastBackupAt(now)
            repository.logBackupHistory("SUCCESS", docRecords.size, totalBytes, "Backed up ${docRecords.size} documents")
            repository.logActivity(null, "Google Drive (${docRecords.size})", "BACKED_UP")
            docRecords.size
        }.onFailure { err ->
            repository.logBackupHistory("FAILED", 0, 0L, err.message ?: "Backup error")
        }
    }

    suspend fun restoreFromDrive(
        accessToken: String,
        onProgress: (Int, Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = findBackupFolder(accessToken) ?: error("No ScanPro backup folder found on Drive")
            val manifestId = findExistingFileInFolder(accessToken, folderId, "scanpro_manifest.json")
                ?: error("No backup manifest found")

            val manifestRaw = downloadFileBytes(accessToken, manifestId).toString(Charsets.UTF_8)
            val manifest = json.decodeFromString<DriveBackupManifest>(manifestRaw)

            // Restore optional settings
            findExistingFileInFolder(accessToken, folderId, "scanpro_settings.json")?.let { settingsId ->
                val rawSettings = downloadFileBytes(accessToken, settingsId).toString(Charsets.UTF_8)
                settingsRepository.importSettingsJson(rawSettings)
            }

            // Restore folders
            manifest.folders.forEach { f ->
                repository.createFolder(f.name, f.parentFolderId, f.colorHex)
            }

            val total = manifest.documents.size.coerceAtLeast(1)
            var restoredCount = 0
            manifest.documents.forEachIndexed { idx, rec ->
                val remoteId = rec.driveFileId ?: findExistingFileInFolder(accessToken, folderId, "${rec.id}.pdf")
                if (!remoteId.isNullOrBlank()) {
                    val tmp = repository.storage.newExportCacheFile("restore_${rec.id}.pdf")
                    FileOutputStream(tmp).use { it.write(downloadFileBytes(accessToken, remoteId)) }
                    repository.registerGeneratedPdfDocument(rec.title, tmp)
                    repository.storage.secureDeleteFile(tmp)
                    restoredCount++
                }
                onProgress(idx + 1, total)
            }

            repository.logBackupHistory("RESTORED", restoredCount, 0L, "Restored $restoredCount documents")
            repository.logActivity(null, "Restored ($restoredCount)", "RESTORED")
            restoredCount
        }
    }

    suspend fun deleteRemoteBackup(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = findBackupFolder(accessToken) ?: return@runCatching Unit
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$folderId")
                .delete()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    error("Failed to delete Drive backup: HTTP ${resp.code}")
                }
            }
            Unit
        }
    }

    private fun findBackupFolder(accessToken: String): String? {
        val q = "name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Drive API error HTTP ${resp.code}")
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("files")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
        }
    }

    private fun getOrCreateBackupFolder(accessToken: String): String {
        findBackupFolder(accessToken)?.let { return it }
        val meta = JSONObject().apply {
            put("name", BACKUP_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .post(meta.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Failed to create Drive folder HTTP ${resp.code}")
            JSONObject(resp.body?.string() ?: "{}").getString("id")
        }
    }

    private fun findExistingFileInFolder(accessToken: String, folderId: String, fileName: String): String? {
        val q = "name='$fileName' and '$folderId' in parents and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("files")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
        }
    }

    private fun uploadOrUpdateFile(
        accessToken: String,
        parentFolderId: String,
        existingFileId: String?,
        fileName: String,
        mimeType: String,
        file: File
    ): String {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            if (existingFileId == null) {
                put("parents", org.json.JSONArray(listOf(parentFolderId)))
            }
        }.toString()

        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(file.asRequestBody(mimeType.toMediaType()))
            .build()

        val reqBuilder = Request.Builder()
            .addHeader("Authorization", "Bearer $accessToken")

        val req = if (existingFileId != null) {
            reqBuilder
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart")
                .patch(multipart)
                .build()
        } else {
            reqBuilder
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .post(multipart)
                .build()
        }

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Drive upload failed HTTP ${resp.code}")
            JSONObject(resp.body?.string() ?: "{}").getString("id")
        }
    }

    private fun downloadFileBytes(accessToken: String, fileId: String): ByteArray {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Drive download failed HTTP ${resp.code}")
            resp.body?.bytes() ?: ByteArray(0)
        }
    }
}
