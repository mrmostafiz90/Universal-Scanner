package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name COLLATE NOCASE ASC")
    fun observeChildFolders(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameFolder(id: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :docId")
    fun observeDocumentById(docId: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :docId")
    suspend fun getDocumentById(docId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE isDeleted = 0")
    suspend fun getActiveDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE isDeleted = 1")
    suspend fun getDeletedDocumentsOnce(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET isDeleted = 1, deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun softDeleteDocuments(ids: List<String>, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isDeleted = 0, deletedAt = NULL, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restoreDocuments(ids: List<String>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun hardDeleteDocuments(ids: List<String>)

    @Query("UPDATE documents SET folderId = :targetFolderId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveDocumentsToFolder(ids: List<String>, targetFolderId: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFolderFromDocuments(folderId: String)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    fun observePagesForDocument(docId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocumentOnce(docId: String): List<PageEntity>

    @Query("SELECT COUNT(*) FROM pages p INNER JOIN documents d ON p.documentId = d.id WHERE d.isDeleted = 0")
    fun observeTotalActivePageCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: String)

    @Query("DELETE FROM pages WHERE documentId = :docId")
    suspend fun deletePagesForDocument(docId: String)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun observeSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(signature: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignature(id: String)
}

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC LIMIT 30")
    fun observeHistory(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: BackupHistoryEntity)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT 12")
    fun observeRecentActivity(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(item: ActivityLogEntity)
}
