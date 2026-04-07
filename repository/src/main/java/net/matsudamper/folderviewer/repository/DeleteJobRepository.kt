package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.DeleteFileDao
import net.matsudamper.folderviewer.repository.db.DeleteFileEntity
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity

@Singleton
class DeleteJobRepository @Inject internal constructor(
    private val operationDao: OperationDao,
    private val deleteFileDao: DeleteFileDao,
) {
    suspend fun saveJob(name: String, files: List<DeleteFile>): Long {
        val totalFiles = files.count { !it.isDirectory }
        val totalBytes = files.sumOf { it.fileSize }
        val operationId = operationDao.insert(
            OperationEntity(
                type = OperationRepository.OperationType.DELETE.name,
                workerId = null,
                name = name,
                status = OperationRepository.OperationStatus.ENQUEUED.name,
                createdAt = System.currentTimeMillis(),
                totalFiles = totalFiles,
                totalBytes = totalBytes,
            ),
        )
        deleteFileDao.insertAll(
            files.map { file ->
                DeleteFileEntity(
                    operationId = operationId,
                    sourceFileId = Json.encodeToString(file.sourceFileId),
                    fileName = file.fileName,
                    fileSize = file.fileSize,
                    isDirectory = file.isDirectory,
                    parentRelativePath = file.parentRelativePath,
                )
            },
        )
        return operationId
    }

    suspend fun getJobById(id: Long): DeleteJob? {
        val entity = operationDao.getById(id) ?: return null
        return entity.toDeleteJob()
    }

    fun observeJob(id: Long): Flow<DeleteJob?> {
        return operationDao.observeById(id).map { entity ->
            entity?.toDeleteJob()
        }
    }

    fun observeFiles(operationId: Long): Flow<List<DeleteFile>> {
        return deleteFileDao.observeByOperationId(operationId).map { entities ->
            entities.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }
    }

    suspend fun getPendingFiles(operationId: Long): List<DeleteFile> {
        return deleteFileDao.getPendingByOperationId(operationId).mapNotNull { entity ->
            runCatching { entity.toDomain() }.getOrNull()
        }
    }

    suspend fun markFileCompleted(fileId: Long) {
        deleteFileDao.markCompleted(fileId)
    }

    suspend fun markFileFailed(fileId: Long, errorMessage: String?) {
        deleteFileDao.markFailed(fileId, errorMessage)
    }

    suspend fun updateProgress(
        operationId: Long,
        completedFiles: Int,
        completedBytes: Long,
        failedFiles: Int,
        currentFileName: String?,
    ) {
        operationDao.updateCompletedProgress(
            id = operationId,
            completedFiles = completedFiles,
            completedBytes = completedBytes,
            failedFiles = failedFiles,
        )
        operationDao.updateCurrentFile(
            id = operationId,
            currentFileName = currentFileName,
            currentFileBytes = 0L,
            currentFileTotalBytes = 0L,
        )
    }

    suspend fun updateStatus(operationId: Long, status: OperationRepository.OperationStatus, workerId: String? = null) {
        operationDao.updateStatusAndWorkerId(id = operationId, status = status.name, workerId = workerId)
    }

    suspend fun updateError(operationId: Long, errorMessage: String?, errorCause: String?) {
        operationDao.updateError(
            id = operationId,
            status = OperationRepository.OperationStatus.FAILED.name,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    suspend fun deleteJob(operationId: Long) {
        operationDao.deleteById(operationId)
    }

    private fun OperationEntity.toDeleteJob(): DeleteJob {
        return DeleteJob(
            id = id,
            workerId = workerId,
            name = name,
            status = runCatching { OperationRepository.OperationStatus.valueOf(status) }
                .getOrDefault(OperationRepository.OperationStatus.FAILED),
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            failedFiles = failedFiles,
            totalBytes = totalBytes,
            completedBytes = completedBytes,
            currentFileName = currentFileName,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    private fun DeleteFileEntity.toDomain(): DeleteFile {
        return DeleteFile(
            id = id,
            operationId = operationId,
            sourceFileId = Json.decodeFromString<FileObjectId.Item>(sourceFileId),
            fileName = fileName,
            fileSize = fileSize,
            isDirectory = isDirectory,
            parentRelativePath = parentRelativePath,
            completed = completed,
            errorMessage = errorMessage,
        )
    }

    data class DeleteJob(
        val id: Long,
        val workerId: String?,
        val name: String,
        val status: OperationRepository.OperationStatus,
        val totalFiles: Int,
        val completedFiles: Int,
        val failedFiles: Int,
        val totalBytes: Long,
        val completedBytes: Long,
        val currentFileName: String?,
        val errorMessage: String?,
        val errorCause: String?,
    )

    data class DeleteFile(
        val id: Long = 0,
        val operationId: Long,
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val isDirectory: Boolean,
        val parentRelativePath: String = "",
        val completed: Boolean = false,
        val errorMessage: String? = null,
    )
}
