package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.PasteFileDao
import net.matsudamper.folderviewer.repository.db.PasteFileEntity
import net.matsudamper.folderviewer.repository.db.PasteJobDao
import net.matsudamper.folderviewer.repository.db.PasteJobEntity

@Singleton
class PasteJobRepository @Inject internal constructor(
    private val pasteJobDao: PasteJobDao,
    private val pasteFileDao: PasteFileDao,
) {
    fun getAllJobs(): Flow<List<PasteJob>> {
        return pasteJobDao.getAllJobs().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.toDomain() }.getOrNull()
            }
        }
    }

    suspend fun getJobById(id: Long): PasteJob? {
        val entity = pasteJobDao.getJobById(id) ?: return null
        return runCatching { entity.toDomain() }.getOrNull()
    }

    suspend fun getJobByWorkerId(workerId: String): PasteJob? {
        val entity = pasteJobDao.getJobByWorkerId(workerId) ?: return null
        return runCatching { entity.toDomain() }.getOrNull()
    }

    suspend fun saveJob(job: PasteJob, files: List<PasteFile>): Long {
        val jobId = pasteJobDao.insert(
            PasteJobEntity(
                workerId = job.workerId,
                mode = job.mode.name,
                destinationFileObjectId = Json.encodeToString(job.destinationFileObjectId),
                destinationDisplayPath = job.destinationDisplayPath,
                totalFiles = job.totalFiles,
                totalBytes = job.totalBytes,
                status = job.status.name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        pasteFileDao.insertAll(
            files.map { file ->
                PasteFileEntity(
                    jobId = jobId,
                    sourceFileId = Json.encodeToString(file.sourceFileId),
                    fileName = file.fileName,
                    fileSize = file.fileSize,
                    destinationRelativePath = file.destinationRelativePath,
                )
            },
        )
        return jobId
    }

    suspend fun getFiles(jobId: Long): List<PasteFile> {
        return pasteFileDao.getFilesByJobId(jobId).mapNotNull { entity ->
            runCatching {
                PasteFile(
                    id = entity.id,
                    jobId = entity.jobId,
                    sourceFileId = Json.decodeFromString<FileObjectId.Item>(entity.sourceFileId),
                    fileName = entity.fileName,
                    fileSize = entity.fileSize,
                    completed = entity.completed,
                    deleted = entity.deleted,
                    destinationRelativePath = entity.destinationRelativePath,
                )
            }.getOrNull()
        }
    }

    suspend fun updateProgress(jobId: Long, progress: ProgressUpdate) {
        pasteJobDao.updateCompletedProgress(
            id = jobId,
            completedFiles = progress.completedFiles,
            completedBytes = progress.completedBytes,
        )
        pasteJobDao.updateCurrentFile(
            id = jobId,
            currentFileName = progress.currentFileName,
            currentFileBytes = progress.currentFileBytes,
            currentFileTotalBytes = progress.currentFileTotalBytes,
        )
    }

    suspend fun markFileCompleted(fileId: Long) {
        pasteFileDao.markCompleted(fileId)
    }

    suspend fun markFileDeleted(fileId: Long) {
        pasteFileDao.markDeleted(fileId)
    }

    suspend fun updateStatus(jobId: Long, status: PasteJobStatus, workerId: String? = null) {
        pasteJobDao.updateStatusAndWorkerId(id = jobId, status = status.name, workerId = workerId)
    }

    suspend fun updateError(jobId: Long, errorMessage: String?, errorCause: String?) {
        pasteJobDao.updateError(
            id = jobId,
            status = PasteJobStatus.FAILED.name,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    suspend fun deleteJob(jobId: Long) {
        pasteJobDao.deleteById(jobId)
    }

    private fun PasteJobEntity.toDomain(): PasteJob {
        return PasteJob(
            id = id,
            workerId = workerId,
            mode = ClipboardRepository.ClipboardMode.valueOf(mode),
            destinationFileObjectId = Json.decodeFromString(destinationFileObjectId),
            destinationDisplayPath = destinationDisplayPath,
            totalFiles = totalFiles,
            totalBytes = totalBytes,
            status = PasteJobStatus.valueOf(status),
            currentFileName = currentFileName,
            currentFileBytes = currentFileBytes,
            currentFileTotalBytes = currentFileTotalBytes,
            completedFiles = completedFiles,
            completedBytes = completedBytes,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    enum class PasteJobStatus {
        RUNNING, PAUSED, COMPLETED, FAILED
    }

    data class PasteJob(
        val id: Long = 0,
        val workerId: String?,
        val mode: ClipboardRepository.ClipboardMode,
        val destinationFileObjectId: FileObjectId,
        val destinationDisplayPath: String,
        val totalFiles: Int,
        val totalBytes: Long,
        val status: PasteJobStatus,
        val currentFileName: String? = null,
        val currentFileBytes: Long = 0,
        val currentFileTotalBytes: Long = 0,
        val completedFiles: Int = 0,
        val completedBytes: Long = 0,
        val errorMessage: String? = null,
        val errorCause: String? = null,
    )

    data class ProgressUpdate(
        val completedFiles: Int,
        val completedBytes: Long,
        val currentFileName: String?,
        val currentFileBytes: Long,
        val currentFileTotalBytes: Long,
    )

    data class PasteFile(
        val id: Long = 0,
        val jobId: Long,
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val completed: Boolean = false,
        val deleted: Boolean = false,
        val destinationRelativePath: String = "",
    )
}
