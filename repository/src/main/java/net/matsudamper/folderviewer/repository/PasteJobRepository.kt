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
import net.matsudamper.folderviewer.repository.db.PasteOperationDao
import net.matsudamper.folderviewer.repository.db.PasteOperationEntity

@Singleton
class PasteJobRepository @Inject internal constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val pasteOperationDao: PasteOperationDao,
    private val operationFileDao: OperationFileDao,
) {
    suspend fun createJob(
        mode: ClipboardRepository.ClipboardMode,
        destinationFileObjectId: FileObjectId,
        destinationDisplayPath: String,
        files: List<NewPasteFile>,
    ): Long {
        val modeText = when (mode) {
            ClipboardRepository.ClipboardMode.Copy -> "コピー"
            ClipboardRepository.ClipboardMode.Cut -> "カット"
        }
        val name = "${files.count { !it.isDirectory }}ファイルを$modeText"
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
                    type = OperationRepository.OperationType.PASTE.name,
                    workerId = null,
                    name = name,
                    description = description,
                    status = OperationRepository.OperationStatus.ENQUEUED.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            pasteOperationDao.insert(
                PasteOperationEntity(
                    operationId = operationId,
                    mode = mode.name,
                    destinationFileObjectId = Json.encodeToString(destinationFileObjectId),
                    destinationDisplayPath = destinationDisplayPath,
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

    suspend fun getJobMeta(jobId: Long): PasteJobMeta? {
        val operation = operationDao.getById(jobId) ?: return null
        val pasteDetail = pasteOperationDao.getByOperationId(jobId) ?: return null
        val mode = ClipboardRepository.ClipboardMode.entries
            .firstOrNull { it.name == pasteDetail.mode } ?: return null
        val destination = runCatching {
            Json.decodeFromString<FileObjectId>(pasteDetail.destinationFileObjectId)
        }.getOrNull() ?: return null
        return PasteJobMeta(
            id = operation.id,
            mode = mode,
            destinationFileObjectId = destination,
            destinationDisplayPath = pasteDetail.destinationDisplayPath,
        )
    }

    suspend fun getFiles(jobId: Long): List<PasteFile> {
        return operationFileDao.getByOperationId(jobId).mapNotNull { it.toPasteFile() }
    }

    suspend fun getPendingFiles(jobId: Long): List<PasteFile> {
        return operationFileDao.getPendingByOperationId(jobId).mapNotNull { it.toPasteFile() }
    }

    fun observeDuplicateFiles(jobId: Long): Flow<List<PasteFile>> {
        return operationFileDao.observeDuplicatesByOperationId(jobId).map { entities ->
            entities.mapNotNull { it.toPasteFile() }
        }
    }

    fun observeCompletedFiles(jobId: Long): Flow<List<PasteFile>> {
        return operationFileDao.observeCompletedByOperationId(jobId).map { entities ->
            entities.mapNotNull { it.toPasteFile() }
        }
    }

    fun observeFailedFiles(jobId: Long): Flow<List<PasteFile>> {
        return operationFileDao.observeFailedByOperationId(jobId).map { entities ->
            entities.mapNotNull { it.toPasteFile() }
        }
    }

    suspend fun finishFileAndStartNext(finish: FileFinish?, nextFileId: Long?) {
        database.withTransaction {
            when (finish) {
                is FileFinish.Completed -> operationFileDao.markCompleted(finish.fileId)
                is FileFinish.Failed -> operationFileDao.markFailed(finish.fileId, finish.errorMessage)
                is FileFinish.Duplicated -> operationFileDao.markDuplicate(
                    fileId = finish.fileId,
                    destinationFileId = Json.encodeToString(finish.destinationFileId),
                    destinationFileSize = finish.destinationFileSize,
                )
                null -> Unit
            }
            if (nextFileId != null) {
                operationFileDao.markRunning(nextFileId)
            }
        }
    }

    suspend fun updateFileTransferred(fileId: Long, bytes: Long) {
        operationFileDao.updateTransferredBytes(fileId, bytes)
    }

    suspend fun markFileCompleted(fileId: Long) {
        operationFileDao.markCompleted(fileId)
    }

    suspend fun markFileFailed(fileId: Long, errorMessage: String?) {
        operationFileDao.markFailed(fileId, errorMessage)
    }

    suspend fun markFileSourceDeleted(fileId: Long) {
        operationFileDao.markSourceDeleted(fileId)
    }

    suspend fun resolveFile(fileId: Long, resolution: DuplicateResolution) {
        operationFileDao.updateResolution(fileId, resolution.name)
    }

    suspend fun countUnresolvedDuplicates(jobId: Long): Int {
        return operationFileDao.countUnresolvedDuplicates(jobId)
    }

    suspend fun countPendingDuplicates(jobId: Long): Int {
        return operationFileDao.countPendingDuplicates(jobId)
    }

    suspend fun countFailedFiles(jobId: Long): Int {
        return operationFileDao.countFailed(jobId)
    }

    suspend fun resetRunningFiles(jobId: Long) {
        database.withTransaction {
            resetRunningFilesInTransaction(jobId)
        }
    }

    suspend fun isPauseRequested(jobId: Long): Boolean {
        return operationDao.isPauseRequested(jobId)
    }

    suspend fun updateStatus(jobId: Long, status: OperationRepository.OperationStatus, workerId: String? = null) {
        operationDao.updateStatusAndWorkerId(id = jobId, status = status.name, workerId = workerId)
    }

    suspend fun pauseJob(jobId: Long) {
        database.withTransaction {
            resetRunningFilesInTransaction(jobId)
            operationDao.updateStatusAndWorkerId(
                id = jobId,
                status = OperationRepository.OperationStatus.PAUSED.name,
                workerId = null,
            )
        }
    }

    suspend fun cancelJob(jobId: Long) {
        database.withTransaction {
            resetRunningFilesInTransaction(jobId)
            operationDao.updateStatusAndWorkerId(
                id = jobId,
                status = OperationRepository.OperationStatus.PAUSED.name,
                workerId = null,
            )
        }
    }

    suspend fun resetFailedFiles(jobId: Long) {
        operationFileDao.resetFailedToPending(jobId)
    }

    private suspend fun resetRunningFilesInTransaction(jobId: Long) {
        operationFileDao.markRunningPartialAsOverwrite(jobId)
        operationFileDao.resetRunningToPending(jobId)
    }

    suspend fun updateError(jobId: Long, errorMessage: String?, errorCause: String?) {
        database.withTransaction {
            resetRunningFilesInTransaction(jobId)
            operationDao.updateError(
                id = jobId,
                status = OperationRepository.OperationStatus.FAILED.name,
                errorMessage = errorMessage,
                errorCause = errorCause,
            )
        }
    }

    private fun OperationFileEntity.toPasteFile(): PasteFile? {
        val source = sourceFileId?.let {
            runCatching { Json.decodeFromString<FileObjectId.Item>(it) }.getOrNull()
        } ?: return null
        return PasteFile(
            id = id,
            operationId = operationId,
            sourceFileId = source,
            fileName = fileName,
            fileSize = fileSize ?: 0,
            relativePath = relativePath,
            isDirectory = isDirectory,
            status = OperationRepository.FileStatus.entries.firstOrNull { it.name == status }
                ?: OperationRepository.FileStatus.FAILED,
            sourceDeleted = sourceDeleted,
            destinationFileId = destinationFileId?.let {
                runCatching { Json.decodeFromString<FileObjectId.Item>(it) }.getOrNull()
            },
            destinationFileSize = destinationFileSize ?: 0,
            resolution = resolution?.let { value ->
                DuplicateResolution.entries.firstOrNull { it.name == value }
            },
            errorMessage = errorMessage,
        )
    }

    enum class DuplicateResolution {
        PENDING, KEEP_DESTINATION, OVERWRITE_WITH_SOURCE
    }

    sealed interface FileFinish {
        data class Completed(val fileId: Long) : FileFinish
        data class Failed(val fileId: Long, val errorMessage: String?) : FileFinish
        data class Duplicated(
            val fileId: Long,
            val destinationFileId: FileObjectId.Item,
            val destinationFileSize: Long,
        ) : FileFinish
    }

    data class PasteJobMeta(
        val id: Long,
        val mode: ClipboardRepository.ClipboardMode,
        val destinationFileObjectId: FileObjectId,
        val destinationDisplayPath: String,
    )

    data class NewPasteFile(
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val relativePath: String = "",
        val isDirectory: Boolean = false,
    )

    data class PasteFile(
        val id: Long,
        val operationId: Long,
        val sourceFileId: FileObjectId.Item,
        val fileName: String,
        val fileSize: Long,
        val relativePath: String,
        val isDirectory: Boolean,
        val status: OperationRepository.FileStatus,
        val sourceDeleted: Boolean,
        val destinationFileId: FileObjectId.Item?,
        val destinationFileSize: Long,
        val resolution: DuplicateResolution?,
        val errorMessage: String?,
    )
}
