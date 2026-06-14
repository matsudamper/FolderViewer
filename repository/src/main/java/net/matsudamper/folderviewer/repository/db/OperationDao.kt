package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OperationDao {
    @Query("$PROGRESS_QUERY GROUP BY op.id ORDER BY op.createdAt DESC, op.id DESC LIMIT :limit")
    fun observeProgress(limit: Int): Flow<List<OperationProgressRow>>

    @Query("$PROGRESS_QUERY WHERE op.id = :id GROUP BY op.id")
    fun observeProgressById(id: Long): Flow<OperationProgressRow?>

    @Query("$PROGRESS_QUERY WHERE op.workerId = :workerId GROUP BY op.id ORDER BY op.createdAt DESC LIMIT 1")
    fun observeProgressByWorkerId(workerId: String): Flow<OperationProgressRow?>

    @Query("SELECT * FROM operations WHERE id = :id")
    suspend fun getById(id: Long): OperationEntity?

    @Query("SELECT * FROM operations WHERE workerId = :workerId")
    suspend fun getByWorkerId(workerId: String): OperationEntity?

    @Insert
    suspend fun insert(operation: OperationEntity): Long

    @Query("UPDATE operations SET status = :status, workerId = :workerId, pauseRequested = 0 WHERE id = :id")
    suspend fun updateStatusAndWorkerId(id: Long, status: String, workerId: String?)

    @Query(
        "UPDATE operations SET status = :status, errorMessage = :errorMessage, errorCause = :errorCause, " +
            "pauseRequested = 0 WHERE id = :id",
    )
    suspend fun updateError(id: Long, status: String, errorMessage: String?, errorCause: String?)

    @Query("UPDATE operations SET pauseRequested = 1 WHERE id = :id AND status = 'RUNNING'")
    suspend fun requestPause(id: Long)

    @Query("SELECT pauseRequested FROM operations WHERE id = :id")
    suspend fun isPauseRequested(id: Long): Boolean

    @Query(
        "DELETE FROM operations WHERE status NOT IN ('RUNNING', 'ENQUEUED')",
    )
    suspend fun deleteNonActive()

    companion object {
        private const val PROGRESS_QUERY =
            "SELECT op.*, po.mode AS pasteMode, " +
                "COUNT(CASE WHEN f.isDirectory = 0 THEN 1 END) AS totalFiles, " +
                "COUNT(CASE WHEN f.isDirectory = 0 AND f.status = 'COMPLETED' THEN 1 END) AS completedFiles, " +
                "COUNT(CASE WHEN f.status = 'FAILED' THEN 1 END) AS failedFiles, " +
                "COUNT(CASE WHEN f.resolution = 'PENDING' THEN 1 END) AS unresolvedDuplicateFiles, " +
                "COALESCE(SUM(CASE WHEN f.isDirectory = 0 THEN COALESCE(f.fileSize, 0) END), 0) AS totalBytes, " +
                "COALESCE(SUM(CASE WHEN f.isDirectory = 0 THEN " +
                "CASE WHEN f.status = 'COMPLETED' THEN COALESCE(f.fileSize, f.transferredBytes) " +
                "ELSE f.transferredBytes END END), 0) AS completedBytes, " +
                "MAX(CASE WHEN f.status = 'RUNNING' THEN " +
                "CASE WHEN f.relativePath = '' THEN f.fileName " +
                "ELSE f.relativePath || '/' || f.fileName END END) AS currentFileName, " +
                "MAX(CASE WHEN f.status = 'RUNNING' THEN f.transferredBytes END) AS currentFileBytes, " +
                "MAX(CASE WHEN f.status = 'RUNNING' THEN f.fileSize END) AS currentFileTotalBytes " +
                "FROM operations AS op " +
                "LEFT JOIN paste_operations AS po ON po.operationId = op.id " +
                "LEFT JOIN operation_files AS f ON f.operationId = op.id"
    }
}
