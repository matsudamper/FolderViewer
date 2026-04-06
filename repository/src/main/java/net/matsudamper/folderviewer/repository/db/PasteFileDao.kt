package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface PasteFileDao {
    @Query("SELECT * FROM paste_files WHERE jobId = :jobId ORDER BY id ASC")
    suspend fun getFilesByJobId(jobId: Long): List<PasteFileEntity>

    @Insert
    suspend fun insertAll(files: List<PasteFileEntity>)

    @Query("UPDATE paste_files SET completed = 1 WHERE id = :fileId")
    suspend fun markCompleted(fileId: Long)

    @Query("UPDATE paste_files SET deleted = 1 WHERE id = :fileId")
    suspend fun markDeleted(fileId: Long)
}
