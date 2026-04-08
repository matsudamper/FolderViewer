package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasteFileDao {
    @Query("SELECT * FROM paste_files WHERE operationId = :operationId ORDER BY id ASC")
    suspend fun getFilesByOperationId(operationId: Long): List<PasteFileEntity>

    @Insert
    suspend fun insertAll(files: List<PasteFileEntity>)

    @Query("UPDATE paste_files SET completed = 1 WHERE id = :fileId")
    suspend fun markCompleted(fileId: Long)

    @Query("UPDATE paste_files SET deleted = 1 WHERE id = :fileId")
    suspend fun markDeleted(fileId: Long)

    @Query(
        "UPDATE paste_files SET isDuplicate = 1, destinationFileId = :destFileId, " +
            "destinationFileSize = :destFileSize, resolution = 'PENDING' WHERE id = :fileId",
    )
    suspend fun markDuplicate(fileId: Long, destFileId: String, destFileSize: Long)

    @Query("UPDATE paste_files SET resolution = :resolution WHERE id = :fileId")
    suspend fun updateResolution(fileId: Long, resolution: String)

    @Query("UPDATE paste_files SET errorMessage = :errorMessage WHERE id = :fileId")
    suspend fun markFailed(fileId: Long, errorMessage: String?)

    @Query(
        "SELECT * FROM paste_files WHERE operationId = :operationId AND isDuplicate = 1 AND completed = 0 ORDER BY id ASC",
    )
    fun observeDuplicatesByOperationId(operationId: Long): Flow<List<PasteFileEntity>>

    @Query(
        "SELECT * FROM paste_files WHERE operationId = :operationId AND completed = 1 ORDER BY id ASC",
    )
    fun observeCompletedNonDuplicatesByOperationId(operationId: Long): Flow<List<PasteFileEntity>>

    @Query(
        "SELECT * FROM paste_files WHERE operationId = :operationId AND errorMessage IS NOT NULL ORDER BY id ASC",
    )
    fun observeFailedByOperationId(operationId: Long): Flow<List<PasteFileEntity>>

    @Query(
        "SELECT COUNT(*) FROM paste_files WHERE operationId = :operationId AND isDuplicate = 1 AND resolution = 'PENDING'",
    )
    suspend fun countUnresolvedDuplicates(operationId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM paste_files WHERE operationId = :operationId AND errorMessage IS NOT NULL",
    )
    suspend fun countFailedFiles(operationId: Long): Int
}
