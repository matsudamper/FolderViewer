package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationProgressRow

@Singleton
class OperationRepository @Inject internal constructor(
    private val operationDao: OperationDao,
) {
    fun observeProgress(limit: Int = 500): Flow<List<OperationProgress>> {
        return operationDao.observeProgress(limit).map { rows ->
            rows.map { it.toDomain() }
        }
    }

    fun observeProgressById(id: Long): Flow<OperationProgress?> {
        return operationDao.observeProgressById(id).map { it?.toDomain() }
    }

    fun observeProgressByWorkerId(workerId: String): Flow<OperationProgress?> {
        return operationDao.observeProgressByWorkerId(workerId).map { it?.toDomain() }
    }

    suspend fun deleteNonActiveHistory() {
        operationDao.deleteNonActive()
    }

    suspend fun updateStatusAndWorkerId(id: Long, status: OperationStatus, workerId: String?) {
        operationDao.updateStatusAndWorkerId(id = id, status = status.name, workerId = workerId)
    }

    suspend fun requestPause(id: Long) {
        operationDao.requestPause(id)
    }

    private fun OperationProgressRow.toDomain(): OperationProgress {
        return OperationProgress(
            id = operation.id,
            type = OperationType.entries.firstOrNull { it.name == operation.type },
            workerId = operation.workerId,
            name = operation.name,
            description = operation.description,
            status = OperationStatus.entries.firstOrNull { it.name == operation.status }
                ?: OperationStatus.FAILED,
            pauseRequested = operation.pauseRequested,
            createdAt = operation.createdAt,
            errorMessage = operation.errorMessage,
            errorCause = operation.errorCause,
            pasteMode = pasteMode?.let { mode ->
                ClipboardRepository.ClipboardMode.entries.firstOrNull { it.name == mode }
            },
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            failedFiles = failedFiles,
            unresolvedDuplicateFiles = unresolvedDuplicateFiles,
            totalBytes = totalBytes,
            completedBytes = completedBytes,
            currentFileName = currentFileName,
            currentFileBytes = currentFileBytes,
            currentFileTotalBytes = currentFileTotalBytes,
        )
    }

    enum class OperationType {
        UPLOAD_FILE, UPLOAD_FOLDER, PASTE, DELETE
    }

    enum class OperationStatus {
        ENQUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED, WAITING_RESOLUTION
    }

    enum class FileStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    data class OperationProgress(
        val id: Long,
        val type: OperationType?,
        val workerId: String?,
        val name: String,
        val description: String,
        val status: OperationStatus,
        val pauseRequested: Boolean,
        val createdAt: Long,
        val errorMessage: String?,
        val errorCause: String?,
        val pasteMode: ClipboardRepository.ClipboardMode?,
        val totalFiles: Int,
        val completedFiles: Int,
        val failedFiles: Int,
        val unresolvedDuplicateFiles: Int,
        val totalBytes: Long,
        val completedBytes: Long,
        val currentFileName: String?,
        val currentFileBytes: Long?,
        val currentFileTotalBytes: Long?,
    )
}
