package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE paste_files SET isDuplicate = 1, destinationFileId = :destFileId, destinationFileSize = :destFileSize, resolution = 'PENDING' WHERE id = :fileId")
    suspend fun markDuplicate(fileId: Long, destFileId: String, destFileSize: Long)

    @Query("UPDATE paste_files SET resolution = :resolution WHERE id = :fileId")
    suspend fun updateResolution(fileId: Long, resolution: String)

    @Query("SELECT * FROM paste_files WHERE jobId = :jobId AND isDuplicate = 1 ORDER BY id ASC")
    fun observeDuplicatesByJobId(jobId: Long): Flow<List<PasteFileEntity>>

    @Query("SELECT * FROM paste_files WHERE jobId = :jobId AND completed = 1 AND isDuplicate = 0 ORDER BY id ASC")
    fun observeCompletedNonDuplicatesByJobId(jobId: Long): Flow<List<PasteFileEntity>>

    @Query("SELECT COUNT(*) FROM paste_files WHERE jobId = :jobId AND isDuplicate = 1 AND resolution = 'PENDING'")
    suspend fun countUnresolvedDuplicates(jobId: Long): Int
}
