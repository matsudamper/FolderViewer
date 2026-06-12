package net.matsudamper.folderviewer.repository

import kotlinx.serialization.json.Json
import jakarta.inject.Inject
import jakarta.inject.Singleton
import androidx.room.withTransaction
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.AppDatabase
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity
import net.matsudamper.folderviewer.repository.db.OperationFileDao
import net.matsudamper.folderviewer.repository.db.OperationFileEntity
import net.matsudamper.folderviewer.repository.db.UploadOperationDao
import net.matsudamper.folderviewer.repository.db.UploadOperationEntity

@Singleton
class UploadJobRepository @Inject internal constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val uploadOperationDao: UploadOperationDao,
    private val operationFileDao: OperationFileDao,
) {
    suspend fun createJob(job: NewUploadJob) {
        database.withTransaction {
            val type = if (job.isFolder) {
                OperationRepository.OperationType.UPLOAD_FOLDER
            } else {
                OperationRepository.OperationType.UPLOAD_FILE
            }
            val operationId = operationDao.insert(
                OperationEntity(
                    type = type.name,
                    workerId = job.workerId,
                    name = job.name,
                    description = job.name,
                    status = OperationRepository.OperationStatus.ENQUEUED.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            uploadOperationDao.insert(
                UploadOperationEntity(
                    operationId = operationId,
                    isFolder = job.isFolder,
                    storageId = Json.encodeToString(job.fileObjectId.storageId),
                    fileObjectId = Json.encodeToString(job.fileObjectId),
                    displayPath = job.displayPath,
                ),
            )
            operationFileDao.insertAll(
                job.files.map { file ->
                    OperationFileEntity(
                        operationId = operationId,
                        fileName = file.fileName,
                        relativePath = file.relativePath,
                        fileSize = file.fileSize,
                    )
                },
            )
        }
    }

    suspend fun getJob(workerId: String): UploadJob? {
        val operation = operationDao.getByWorkerId(workerId) ?: return null
        val uploadDetail = uploadOperationDao.getByOperationId(operation.id) ?: return null
        val fileObjectId = runCatching {
            Json.decodeFromString<FileObjectId>(uploadDetail.fileObjectId)
        }.getOrNull() ?: return null
        return UploadJob(
            operationId = operation.id,
            workerId = workerId,
            name = operation.name,
            isFolder = uploadDetail.isFolder,
            fileObjectId = fileObjectId,
            displayPath = uploadDetail.displayPath,
        )
    }

    suspend fun getFiles(operationId: Long): List<UploadFile> {
        return operationFileDao.getByOperationId(operationId).map { entity ->
            UploadFile(
                id = entity.id,
                fileName = entity.fileName,
                relativePath = entity.relativePath,
                fileSize = entity.fileSize,
                status = OperationRepository.FileStatus.entries.firstOrNull { it.name == entity.status }
                    ?: OperationRepository.FileStatus.FAILED,
            )
        }
    }

    suspend fun startFile(fileId: Long, fileSize: Long?) {
        database.withTransaction {
            if (fileSize != null) {
                operationFileDao.updateFileSize(fileId, fileSize)
            }
            operationFileDao.markRunning(fileId)
        }
    }

    suspend fun updateFileSize(fileId: Long, fileSize: Long) {
        operationFileDao.updateFileSize(fileId, fileSize)
    }

    suspend fun updateFileTransferred(fileId: Long, bytes: Long) {
        operationFileDao.updateTransferredBytes(fileId, bytes)
    }

    suspend fun applyFolderProgress(completedFileIds: List<Long>, runningFileId: Long?, transferredBytes: Long) {
        database.withTransaction {
            completedFileIds.forEach { fileId ->
                operationFileDao.markCompleted(fileId)
            }
            if (runningFileId != null) {
                operationFileDao.markRunning(runningFileId)
                operationFileDao.updateTransferredBytes(runningFileId, transferredBytes)
            }
        }
    }

    suspend fun completeJob(workerId: String) {
        database.withTransaction {
            val operation = operationDao.getByWorkerId(workerId) ?: return@withTransaction
            operationFileDao.markAllCompleted(operation.id)
            operationDao.updateStatusAndWorkerId(
                id = operation.id,
                status = OperationRepository.OperationStatus.COMPLETED.name,
                workerId = workerId,
            )
        }
    }

    suspend fun cancelJob(workerId: String) {
        database.withTransaction {
            val operation = operationDao.getByWorkerId(workerId) ?: return@withTransaction
            operationFileDao.resetRunningToPending(operation.id)
            operationDao.updateStatusAndWorkerId(
                id = operation.id,
                status = OperationRepository.OperationStatus.CANCELLED.name,
                workerId = workerId,
            )
        }
    }

    suspend fun updateError(workerId: String, errorMessage: String?, errorCause: String?) {
        database.withTransaction {
            val operation = operationDao.getByWorkerId(workerId) ?: return@withTransaction
            operationFileDao.resetRunningToPending(operation.id)
            operationDao.updateError(
                id = operation.id,
                status = OperationRepository.OperationStatus.FAILED.name,
                errorMessage = errorMessage,
                errorCause = errorCause,
            )
        }
    }

    suspend fun updateStatus(workerId: String, status: OperationRepository.OperationStatus) {
        val operation = operationDao.getByWorkerId(workerId) ?: return
        operationDao.updateStatusAndWorkerId(
            id = operation.id,
            status = status.name,
            workerId = workerId,
        )
    }

    data class NewUploadJob(
        val workerId: String,
        val name: String,
        val isFolder: Boolean,
        val fileObjectId: FileObjectId,
        val displayPath: String,
        val files: List<NewUploadFile>,
    )

    data class NewUploadFile(
        val fileName: String,
        val relativePath: String = "",
        val fileSize: Long? = null,
    )

    data class UploadJob(
        val operationId: Long,
        val workerId: String,
        val name: String,
        val isFolder: Boolean,
        val fileObjectId: FileObjectId,
        val displayPath: String,
    )

    data class UploadFile(
        val id: Long,
        val fileName: String,
        val relativePath: String,
        val fileSize: Long?,
        val status: OperationRepository.FileStatus,
    )
}
