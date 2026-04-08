package net.matsudamper.folderviewer.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import jakarta.inject.Inject
import jakarta.inject.Singleton
import androidx.room.withTransaction
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.AppDatabase
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity
import net.matsudamper.folderviewer.repository.db.UploadOperationDao
import net.matsudamper.folderviewer.repository.db.UploadOperationEntity

@Singleton
class UploadJobRepository @Inject internal constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val uploadOperationDao: UploadOperationDao,
) {
    fun getAllJobs(): Flow<List<UploadJob>> {
        return operationDao.observeAll(limit = 500).map { entities ->
            entities
                .filter {
                    it.type == OperationRepository.OperationType.UPLOAD_FILE.name ||
                        it.type == OperationRepository.OperationType.UPLOAD_FOLDER.name
                }
                .mapNotNull { entity ->
                    runCatching { entity.toUploadJob(uploadOperationDao) }.getOrNull()
                }
        }
    }

    suspend fun getJob(workerId: String): UploadJob? {
        val entity = operationDao.getByWorkerId(workerId) ?: return null
        return runCatching { entity.toUploadJob(uploadOperationDao) }.getOrNull()
    }

    suspend fun saveJob(job: UploadJob) {
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
        }
    }

    suspend fun deleteJob(workerId: String) {
        val entity = operationDao.getByWorkerId(workerId) ?: return
        operationDao.deleteById(entity.id)
    }

    suspend fun updateError(workerId: String, errorMessage: String?, errorCause: String?) {
        val entity = operationDao.getByWorkerId(workerId) ?: return
        operationDao.updateError(
            id = entity.id,
            status = OperationRepository.OperationStatus.FAILED.name,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    suspend fun updateStatus(workerId: String, status: OperationRepository.OperationStatus) {
        val entity = operationDao.getByWorkerId(workerId) ?: return
        operationDao.updateStatusAndWorkerId(
            id = entity.id,
            status = status.name,
            workerId = workerId,
        )
    }

    private suspend fun OperationEntity.toUploadJob(uploadOperationDao: UploadOperationDao): UploadJob {
        val uploadDetail = uploadOperationDao.getByOperationId(id)
            ?: error("UploadOperationEntity not found for operationId=$id")
        return UploadJob(
            workerId = requireNotNull(workerId),
            name = name,
            isFolder = uploadDetail.isFolder,
            fileObjectId = Json.decodeFromString<FileObjectId>(uploadDetail.fileObjectId),
            displayPath = uploadDetail.displayPath,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    data class UploadJob(
        val workerId: String,
        val name: String,
        val isFolder: Boolean,
        val fileObjectId: FileObjectId,
        val displayPath: String,
        val errorMessage: String? = null,
        val errorCause: String? = null,
    )
}
