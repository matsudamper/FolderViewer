package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OperationFileDao {
    @Insert
    suspend fun insertAll(files: List<OperationFileEntity>)

    @Query("SELECT * FROM operation_files WHERE operationId = :operationId ORDER BY id ASC")
    suspend fun getByOperationId(operationId: Long): List<OperationFileEntity>

    @Query("SELECT * FROM operation_files WHERE operationId = :operationId ORDER BY id ASC")
    fun observeByOperationId(operationId: Long): Flow<List<OperationFileEntity>>

    @Query(
        "SELECT * FROM operation_files WHERE operationId = :operationId " +
            "AND status IN ('PENDING', 'RUNNING') " +
            "AND (resolution IS NULL OR resolution != 'PENDING') " +
            "ORDER BY id ASC",
    )
    suspend fun getPendingByOperationId(operationId: Long): List<OperationFileEntity>

    @Query("UPDATE operation_files SET status = 'RUNNING', transferredBytes = 0 WHERE id = :fileId")
    suspend fun markRunning(fileId: Long)

    @Query("UPDATE operation_files SET transferredBytes = :bytes WHERE id = :fileId")
    suspend fun updateTransferredBytes(fileId: Long, bytes: Long)

    @Query(
        "UPDATE operation_files SET status = 'COMPLETED', " +
            "transferredBytes = COALESCE(fileSize, transferredBytes) WHERE id = :fileId",
    )
    suspend fun markCompleted(fileId: Long)

    @Query(
        "UPDATE operation_files SET status = 'COMPLETED', " +
            "transferredBytes = COALESCE(fileSize, transferredBytes) " +
            "WHERE operationId = :operationId AND status NOT IN ('COMPLETED', 'FAILED')",
    )
    suspend fun markAllCompleted(operationId: Long)

    @Query("UPDATE operation_files SET status = 'FAILED', errorMessage = :errorMessage WHERE id = :fileId")
    suspend fun markFailed(fileId: Long, errorMessage: String?)

    @Query(
        "UPDATE operation_files SET status = 'PENDING', transferredBytes = 0, resolution = 'PENDING', " +
            "destinationFileId = :destinationFileId, destinationFileSize = :destinationFileSize " +
            "WHERE id = :fileId",
    )
    suspend fun markDuplicate(fileId: Long, destinationFileId: String, destinationFileSize: Long)

    @Query("UPDATE operation_files SET resolution = :resolution WHERE id = :fileId")
    suspend fun updateResolution(fileId: Long, resolution: String)

    @Query("UPDATE operation_files SET sourceDeleted = 1 WHERE id = :fileId")
    suspend fun markSourceDeleted(fileId: Long)

    @Query("UPDATE operation_files SET fileSize = :fileSize WHERE id = :fileId")
    suspend fun updateFileSize(fileId: Long, fileSize: Long)

    @Query(
        "UPDATE operation_files SET status = 'PENDING', transferredBytes = 0 " +
            "WHERE operationId = :operationId AND status = 'RUNNING'",
    )
    suspend fun resetRunningToPending(operationId: Long)

    @Query(
        "UPDATE operation_files SET status = 'PENDING', transferredBytes = 0, errorMessage = NULL " +
            "WHERE operationId = :operationId AND status = 'FAILED'",
    )
    suspend fun resetFailedToPending(operationId: Long)

    @Query(
        "UPDATE operation_files SET resolution = 'OVERWRITE_WITH_SOURCE' " +
            "WHERE operationId = :operationId AND status = 'RUNNING' " +
            "AND isDirectory = 0 AND transferredBytes > 0 AND resolution IS NULL",
    )
    suspend fun markRunningPartialAsOverwrite(operationId: Long)

    @Query("SELECT COUNT(*) FROM operation_files WHERE operationId = :operationId AND resolution = 'PENDING'")
    suspend fun countUnresolvedDuplicates(operationId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM operation_files WHERE operationId = :operationId " +
            "AND status IN ('PENDING', 'RUNNING') AND resolution IS NOT NULL",
    )
    suspend fun countPendingDuplicates(operationId: Long): Int

    @Query("SELECT COUNT(*) FROM operation_files WHERE operationId = :operationId AND status = 'FAILED'")
    suspend fun countFailed(operationId: Long): Int

    @Query(
        "SELECT * FROM operation_files WHERE operationId = :operationId " +
            "AND destinationFileId IS NOT NULL " +
            "AND resolution IS NOT NULL AND status != 'COMPLETED' ORDER BY id ASC",
    )
    fun observeDuplicatesByOperationId(operationId: Long): Flow<List<OperationFileEntity>>

    @Query(
        "SELECT * FROM operation_files WHERE operationId = :operationId AND status = 'COMPLETED' ORDER BY id ASC",
    )
    fun observeCompletedByOperationId(operationId: Long): Flow<List<OperationFileEntity>>

    @Query(
        "SELECT * FROM operation_files WHERE operationId = :operationId AND status = 'FAILED' ORDER BY id ASC",
    )
    fun observeFailedByOperationId(operationId: Long): Flow<List<OperationFileEntity>>
}
