package net.matsudamper.folderviewer.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.db.UploadJobDao
import net.matsudamper.folderviewer.repository.db.UploadJobEntity

@Singleton
class UploadJobRepository @Inject internal constructor(
    private val uploadJobDao: UploadJobDao,
) {
    fun getAllJobs(): Flow<List<UploadJob>> {
        return uploadJobDao.getAllJobs().map { entities ->
            entities.mapNotNull { entity ->
                runCatching {
                    UploadJob(
                        workerId = entity.workerId,
                        name = entity.name,
                        isFolder = entity.isFolder,
                        storageId = Json.decodeFromString<StorageId>(entity.storageId),
                        fileObjectId = Json.decodeFromString<FileObjectId>(entity.fileObjectId),
                    )
                }.getOrNull()
            }
        }
    }

    suspend fun getJob(workerId: String): UploadJob? {
        val entity = uploadJobDao.getJob(workerId) ?: return null
        return runCatching {
            UploadJob(
                workerId = entity.workerId,
                name = entity.name,
                isFolder = entity.isFolder,
                storageId = Json.decodeFromString<StorageId>(entity.storageId),
                fileObjectId = Json.decodeFromString<FileObjectId>(entity.fileObjectId),
            )
        }.getOrNull()
    }

    suspend fun saveJob(
        workerId: String,
        name: String,
        isFolder: Boolean,
        storageId: StorageId,
        fileObjectId: FileObjectId,
    ) {
        uploadJobDao.insert(
            UploadJobEntity(
                workerId = workerId,
                name = name,
                isFolder = isFolder,
                storageId = Json.encodeToString(storageId),
                fileObjectId = Json.encodeToString(fileObjectId),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteJob(workerId: String) {
        uploadJobDao.delete(workerId)
    }

    data class UploadJob(
        val workerId: String,
        val name: String,
        val isFolder: Boolean,
        val storageId: StorageId,
        val fileObjectId: FileObjectId,
    )
}
