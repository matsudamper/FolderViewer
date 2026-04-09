package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OperationDao {
    @Query("SELECT * FROM operations ORDER BY createdAt DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE id = :id")
    fun observeById(id: Long): Flow<OperationEntity?>

    @Query("SELECT * FROM operations WHERE id = :id")
    suspend fun getById(id: Long): OperationEntity?

    @Query("SELECT * FROM operations WHERE workerId = :workerId")
    suspend fun getByWorkerId(workerId: String): OperationEntity?

    @Insert
    suspend fun insert(operation: OperationEntity): Long

    @Query("UPDATE operations SET status = :status, workerId = :workerId WHERE id = :id")
    suspend fun updateStatusAndWorkerId(id: Long, status: String, workerId: String?)

    @Query(
        "UPDATE operations SET status = :status, errorMessage = :errorMessage, errorCause = :errorCause WHERE id = :id",
    )
    suspend fun updateError(id: Long, status: String, errorMessage: String?, errorCause: String?)

    @Query(
        "UPDATE operations SET completedFiles = :completedFiles, completedBytes = :completedBytes, " +
            "failedFiles = :failedFiles WHERE id = :id",
    )
    suspend fun updateCompletedProgress(id: Long, completedFiles: Int, completedBytes: Long, failedFiles: Int)

    @Query(
        "UPDATE operations SET currentFileName = :currentFileName, currentFileBytes = :currentFileBytes, " +
            "currentFileTotalBytes = :currentFileTotalBytes WHERE id = :id",
    )
    suspend fun updateCurrentFile(id: Long, currentFileName: String?, currentFileBytes: Long, currentFileTotalBytes: Long)

    @Query("UPDATE operations SET duplicateFiles = :duplicateFiles WHERE id = :id")
    suspend fun updateDuplicateCount(id: Long, duplicateFiles: Int)

    @Query("UPDATE operations SET resolvedFiles = :resolvedFiles WHERE id = :id")
    suspend fun updateResolvedCount(id: Long, resolvedFiles: Int)

    @Query("UPDATE operations SET failedFiles = :failedFiles WHERE id = :id")
    suspend fun updateFailedCount(id: Long, failedFiles: Int)

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM operations WHERE status NOT IN ('RUNNING', 'ENQUEUED', 'PAUSED', 'WAITING_RESOLUTION')",
    )
    suspend fun deleteNonActive()
}
