package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.AppDatabase
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity
import net.matsudamper.folderviewer.repository.db.OperationFileDao
import net.matsudamper.folderviewer.repository.db.OperationFileEntity

@Singleton
class DeleteJobRepository @Inject internal constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val operationFileDao: OperationFileDao,
) {
    suspend fun createJob(name: String, files: List<NewDeleteFile>): Long {
        val description = OperationDescription.build(
            files = files.map {
                OperationDescription.File(
                    path = OperationDescription.joinPath(it.relativePath, it.fileName),
                    isDirectory = it.isDirectory,
                )
            },
            fallback = name,
        )
        return database.withTransaction {
            val operationId = operationDao.insert(
                OperationEntity(
                    type = OperationRepository.OperationType.DELETE.name,
                    workerId = null,
                    name = name,
                    description = description,
                    status = OperationRepository.OperationStatus.ENQUEUED.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            operationFileDao.insertAll(
                files.map { file ->
                    OperationFileEntity(
                        operationId = operationId,
                        fileName = file.fileName,
                        relativePath = file.relativePath,
                        isDirectory = file.isDirectory,
                        fileSize = file.fileSize,
                        sourceFileId = Json.encodeToString(file.sourceFileId),
                    )
                },
            )
            operationId
        }
    }

    suspend fun getFiles(operationId: Long): List<DeleteFile> {
        return operationFileDao.getByOperationId(operationId).mapNotNull { it.toDeleteFile() }
    }

    suspend fun getPendingFiles(operationId: Long): List<DeleteFile> {
        return operationFileDao.getPendingByOperationId(operationId).mapNotNull { it.toDeleteFile() }
    }

    fun observeFiles(operationId: Long): Flow<List<DeleteFile>> {
        return operationFileDao.observeByOperationId(operationId).map { entities ->
            entities.mapNotNull { it.toDeleteFile() }
        }
    }

    suspend fun finishFileAndStartNext(finish: FileFinish?, nextFileId: Long?) {
        database.withTransaction {
            when (finish) {
                is FileFinish.Completed -> operationFileDao.markCompleted(finish.fileId)
                is FileFinish.Failed -> operationFileDao.markFailed(finish.fileId, finish.errorMessage)
                null -> Unit
            }
            if (nextFileId != null) {
                operationFileDao.markRunning(nextFileId)
            }
        }
    }

    suspend fun countFailedFiles(operationId: Long): Int {
        return operationFileDao.countFailed(operationId)
    }

    suspend fun resetRunningFiles(operationId: Long) {
        operationFileDao.resetRunningToPending(operationId)
    }

    suspend fun retryJob(operationId: Long) {
        database.withTransaction {
            operationFileDao.resetRunningToPending(operationId)
            operationFileDao.resetFailedToPending(operationId)
        }
    }

    suspend fun updateStatus(operationId: Long, status: OperationRepository.OperationStatus, workerId: String? = null) {
        operationDao.updateStatusAndWorkerId(id = operationId, status = status.name, workerId = workerId)
    }

    suspend fun cancelJob(operationId: Long) {
        database.withTransaction {
            operationFileDao.resetRunningToPending(operationId)
            operationDao.updateStatusAndWorkerId(
                id = operationId,
                status = OperationRepository.OperationStatus.CANCELLED.name,
                workerId = null,
            )
        }
    }

    suspend fun updateError(operationId: Long, errorMessage: String?, errorCause: String?) {
        database.withTransaction {
            operationFileDao.resetRunningToPending(operationId)
            operationDao.updateError(
                id = operationId,
                status = OperationRepository.OperationStatus.FAILED.name,
                errorMessage = errorMessage,
                errorCause = errorCause,
            )
        }
    }

    private fun OperationFileEntity.toDeleteFile(): DeleteFile? {
        val source = sourceFileId?.let {
            runCatching { Json.decodeFromString<FileObjectId.Item>(it) }.getOrNull()
        } ?: return null
        return DeleteFile(
            id = id,
            operationId = operationId,
            sourceFileId = source,
            fileName = fileName,
            fileSize = fileSize ?: 0,
            isDirectory = isDirectory,
            relativePath = relativePath,
            status = OperationRepository.FileStatus.entries.firstOrNull { it.name == status }
                ?: OperationRepository.FileStatus.FAILED,
            errorMessage = errorMessage,
        )
    }

    sealed interface FileFinish {
        data class Completed(val fileId: Long) : FileFinish
        data class Failed(val fileId: Long, val errorMessage: String?) : FileFinish
    }

    data class NewDeleteFile(
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val isDirectory: Boolean,
        val relativePath: String = "",
    )

    data class DeleteFile(
        val id: Long,
        val operationId: Long,
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val isDirectory: Boolean,
        val relativePath: String,
        val status: OperationRepository.FileStatus,
        val errorMessage: String?,
    )
}
