package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity
import net.matsudamper.folderviewer.repository.db.PasteFileDao
import net.matsudamper.folderviewer.repository.db.PasteFileEntity
import net.matsudamper.folderviewer.repository.db.PasteOperationDao
import net.matsudamper.folderviewer.repository.db.PasteOperationEntity

@Singleton
class PasteJobRepository @Inject internal constructor(
    private val operationDao: OperationDao,
    private val pasteOperationDao: PasteOperationDao,
    private val pasteFileDao: PasteFileDao,
) {
    fun getAllJobs(): Flow<List<PasteJob>> {
        return operationDao.observeAll(limit = 500).map { entities ->
            entities
                .filter { it.type == OperationRepository.OperationType.PASTE.name }
                .mapNotNull { entity ->
                    runCatching { entity.toPasteJob() }.getOrNull()
                }
        }
    }

    fun observeJob(id: Long): Flow<PasteJob?> {
        return operationDao.observeById(id).map { entity ->
            entity?.let { runCatching { it.toPasteJob() }.getOrNull() }
        }
    }

    suspend fun getJobById(id: Long): PasteJob? {
        val entity = operationDao.getById(id) ?: return null
        return runCatching { entity.toPasteJob() }.getOrNull()
    }

    suspend fun getJobByWorkerId(workerId: String): PasteJob? {
        val entity = operationDao.getByWorkerId(workerId) ?: return null
        return runCatching { entity.toPasteJob() }.getOrNull()
    }

    suspend fun saveJob(job: PasteJob, files: List<PasteFile>): Long {
        val operationId = operationDao.insert(
            OperationEntity(
                type = OperationRepository.OperationType.PASTE.name,
                workerId = job.workerId,
                name = job.name,
                status = job.status.name,
                createdAt = System.currentTimeMillis(),
                totalFiles = job.totalFiles,
                totalBytes = job.totalBytes,
            ),
        )
        pasteOperationDao.insert(
            PasteOperationEntity(
                operationId = operationId,
                mode = job.mode.name,
                destinationFileObjectId = Json.encodeToString(job.destinationFileObjectId),
                destinationDisplayPath = job.destinationDisplayPath,
            ),
        )
        pasteFileDao.insertAll(
            files.map { file ->
                PasteFileEntity(
                    operationId = operationId,
                    sourceFileId = Json.encodeToString(file.sourceFileId),
                    fileName = file.fileName,
                    fileSize = file.fileSize,
                    destinationRelativePath = file.destinationRelativePath,
                    isDirectory = file.isDirectory,
                )
            },
        )
        return operationId
    }

    suspend fun getFiles(jobId: Long): List<PasteFile> {
        return pasteFileDao.getFilesByOperationId(jobId).mapNotNull { entity ->
            runCatching { entity.toDomain() }.getOrNull()
        }
    }

    fun observeDuplicateFiles(jobId: Long): Flow<List<PasteFile>> {
        return pasteFileDao.observeDuplicatesByOperationId(jobId).map { entities ->
            entities.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }
    }

    fun observeCompletedFiles(jobId: Long): Flow<List<PasteFile>> {
        return pasteFileDao.observeCompletedNonDuplicatesByOperationId(jobId).map { entities ->
            entities.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }
    }

    fun observeFailedFiles(jobId: Long): Flow<List<PasteFile>> {
        return pasteFileDao.observeFailedByOperationId(jobId).map { entities ->
            entities.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }
    }

    suspend fun updateProgress(jobId: Long, progress: ProgressUpdate) {
        operationDao.updateCompletedProgress(
            id = jobId,
            completedFiles = progress.completedFiles,
            completedBytes = progress.completedBytes,
            failedFiles = 0,
        )
        operationDao.updateCurrentFile(
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

    suspend fun markFileFailed(fileId: Long, errorMessage: String?) {
        pasteFileDao.markFailed(fileId, errorMessage)
    }

    suspend fun markFileDuplicate(fileId: Long, destinationFileId: FileObjectId.Item, destinationFileSize: Long) {
        pasteFileDao.markDuplicate(
            fileId = fileId,
            destFileId = Json.encodeToString(destinationFileId),
            destFileSize = destinationFileSize,
        )
    }

    suspend fun resolveFile(fileId: Long, resolution: DuplicateResolution) {
        pasteFileDao.updateResolution(fileId, resolution.name)
    }

    suspend fun countUnresolvedDuplicates(jobId: Long): Int {
        return pasteFileDao.countUnresolvedDuplicates(jobId)
    }

    suspend fun updateStatus(jobId: Long, status: PasteJobStatus, workerId: String? = null) {
        operationDao.updateStatusAndWorkerId(id = jobId, status = status.name, workerId = workerId)
    }

    suspend fun updateError(jobId: Long, errorMessage: String?, errorCause: String?) {
        operationDao.updateError(
            id = jobId,
            status = PasteJobStatus.FAILED.name,
            errorMessage = errorMessage,
            errorCause = errorCause,
        )
    }

    suspend fun updateDuplicateCount(jobId: Long, count: Int) {
        operationDao.updateDuplicateCount(jobId, count)
    }

    suspend fun updateResolvedCount(jobId: Long, count: Int) {
        operationDao.updateResolvedCount(jobId, count)
    }

    suspend fun deleteJob(jobId: Long) {
        operationDao.deleteById(jobId)
    }

    private suspend fun OperationEntity.toPasteJob(): PasteJob {
        val pasteDetail = pasteOperationDao.getByOperationId(id)
            ?: error("PasteOperationEntity not found for operationId=$id")
        val name = "${totalFiles}ファイルを${
            when (pasteDetail.mode) {
                "Copy" -> "コピー"
                else -> "カット"
            }
        }"
        return PasteJob(
            id = id,
            workerId = workerId,
            name = name,
            mode = ClipboardRepository.ClipboardMode.valueOf(pasteDetail.mode),
            destinationFileObjectId = Json.decodeFromString(pasteDetail.destinationFileObjectId),
            destinationDisplayPath = pasteDetail.destinationDisplayPath,
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
            duplicateFiles = duplicateFiles,
            resolvedFiles = resolvedFiles,
        )
    }

    private fun PasteFileEntity.toDomain(): PasteFile {
        return PasteFile(
            id = id,
            jobId = operationId,
            sourceFileId = Json.decodeFromString<FileObjectId.Item>(sourceFileId),
            fileName = fileName,
            fileSize = fileSize,
            completed = completed,
            deleted = deleted,
            destinationRelativePath = destinationRelativePath,
            isDirectory = isDirectory,
            isDuplicate = isDuplicate,
            destinationFileId = destinationFileId?.let {
                runCatching { Json.decodeFromString<FileObjectId.Item>(it) }.getOrNull()
            },
            destinationFileSize = destinationFileSize,
            resolution = resolution?.let { runCatching { DuplicateResolution.valueOf(it) }.getOrNull() },
            errorMessage = errorMessage,
        )
    }

    enum class PasteJobStatus {
        RUNNING, PAUSED, COMPLETED, FAILED, WAITING_RESOLUTION
    }

    enum class DuplicateResolution {
        PENDING, KEEP_DESTINATION, OVERWRITE_WITH_SOURCE
    }

    data class PasteJob(
        val id: Long = 0,
        val workerId: String?,
        val name: String = "",
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
        val duplicateFiles: Int = 0,
        val resolvedFiles: Int = 0,
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
        val isDirectory: Boolean = false,
        val isDuplicate: Boolean = false,
        val destinationFileId: FileObjectId.Item? = null,
        val destinationFileSize: Long = 0,
        val resolution: DuplicateResolution? = null,
        val errorMessage: String? = null,
    )
}
