package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentFolderId"]), Index(value = ["name"])]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val colorHex: Long = 0xFF0F766E,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["isDeleted"]),
        Index(value = ["updatedAt"]),
        Index(value = ["title"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val thumbnailPath: String? = null,
    val pdfPath: String? = null,
    val ocrSummaryText: String = "",
    val isEncrypted: Boolean = false,
    val isPdfPasswordProtected: Boolean = false,
    val bookmarkedPagesCsv: String = "",
    val driveFileId: String? = null,
    val lastBackedUpAt: Long? = null
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"]), Index(value = ["pageIndex"])]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val thumbnailPath: String,
    val adjustmentsJson: String,
    val ocrText: String = "",
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // SUCCESS, FAILED, RESTORED
    val uploadedCount: Int,
    val totalBytes: Long,
    val message: String
)

@Entity(
    tableName = "activity_log",
    indices = [Index(value = ["timestamp"])]
)
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val documentId: String? = null,
    val documentTitle: String,
    val actionType: String, // SCANNED, PDF_EXPORTED, OCR_EXTRACTED, SIGNED, BACKED_UP, RESTORED
    val timestamp: Long = System.currentTimeMillis()
)
