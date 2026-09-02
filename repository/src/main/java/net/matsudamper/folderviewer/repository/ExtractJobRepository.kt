package net.matsudamper.folderviewer.repository

import androidx.room.withTransaction
import java.io.File
import kotlinx.serialization.json.Json
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.db.AppDatabase
import net.matsudamper.folderviewer.repository.db.ExtractOperationDao
import net.matsudamper.folderviewer.repository.db.ExtractOperationEntity
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity

@Singleton
class ExtractJobRepository @Inject internal constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val extractOperationDao: ExtractOperationDao,
) {
    suspend fun createJob(params: NewExtractJob): Long {
        return createJobInternal(
            CreateJobParams(
                sourceFileObjectId = params.sourceFileObjectId,
                sourceFileName = params.sourceFileName,
                outputName = params.outputName,
                extractType = params.extractType,
                parentFileObjectId = params.parentFileObjectId,
                parentDisplayPath = params.parentDisplayPath,
                localFolderPath = params.localFolderPath,
                openOnComplete = params.openOnComplete,
                sourceAbsolutePath = null,
            ),
        )
    }

    suspend fun createExternalJob(params: NewExternalExtractJob): Long {
        return createJobInternal(
            CreateJobParams(
                sourceFileObjectId = ExternalExtractJobIds.sourceFileObjectId,
                sourceFileName = params.sourceFileName,
                outputName = params.outputName,
                extractType = params.extractType,
                parentFileObjectId = ExternalExtractJobIds.parentFileObjectId,
                parentDisplayPath = "",
                localFolderPath = params.outputParentPath,
                openOnComplete = params.openOnComplete,
                sourceAbsolutePath = params.sourceAbsolutePath,
            ),
        )
    }

    private suspend fun createJobInternal(params: CreateJobParams): Long {
        val name = when (params.extractType) {
            ExtractType.Zip,
            ExtractType.TarGz,
            ExtractType.TarXz,
            ExtractType.TarZst,
            -> "${params.sourceFileName}を展開"

            ExtractType.Zst,
            ExtractType.Xz,
            -> "${params.sourceFileName}を展開"
        }
        val description = OperationDescription.build(
            files = listOf(
                OperationDescription.File(
                    path = params.sourceFileName,
                    isDirectory = false,
                ),
            ),
            fallback = name,
        )
        return database.withTransaction {
            val operationId = operationDao.insert(
                OperationEntity(
                    type = OperationRepository.OperationType.EXTRACT.name,
                    workerId = null,
                    name = name,
                    description = description,
                    status = OperationRepository.OperationStatus.ENQUEUED.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            extractOperationDao.insert(
                ExtractOperationEntity(
                    operationId = operationId,
                    sourceFileObjectId = Json.encodeToString(params.sourceFileObjectId),
                    sourceFileName = params.sourceFileName,
                    outputName = params.outputName,
                    extractType = params.extractType.name,
                    parentFileObjectId = Json.encodeToString(params.parentFileObjectId),
                    parentDisplayPath = params.parentDisplayPath,
                    localFolderPath = params.localFolderPath,
                    openOnComplete = params.openOnComplete,
                    sourceAbsolutePath = params.sourceAbsolutePath,
                ),
            )
            operationId
        }
    }

    private data class CreateJobParams(
        val sourceFileObjectId: FileObjectId.Item,
        val sourceFileName: String,
        val outputName: String,
        val extractType: ExtractType,
        val parentFileObjectId: FileObjectId,
        val parentDisplayPath: String,
        val localFolderPath: String,
        val openOnComplete: Boolean,
        val sourceAbsolutePath: String?,
    )

    suspend fun getJobMeta(operationId: Long): ExtractJobMeta? {
        val operation = operationDao.getById(operationId) ?: return null
        val detail = extractOperationDao.getByOperationId(operationId) ?: return null
        val extractType = ExtractType.entries.firstOrNull { it.name == detail.extractType } ?: return null
        val isExternalJob = detail.sourceAbsolutePath != null
        val sourceFileObjectId = if (isExternalJob) {
            null
        } else {
            runCatching {
                Json.decodeFromString<FileObjectId.Item>(detail.sourceFileObjectId)
            }.getOrNull() ?: return null
        }
        val parentFileObjectId = if (isExternalJob) {
            null
        } else {
            runCatching {
                Json.decodeFromString<FileObjectId>(detail.parentFileObjectId)
            }.getOrNull() ?: return null
        }
        return ExtractJobMeta(
            id = operation.id,
            sourceFileObjectId = sourceFileObjectId,
            sourceFileName = detail.sourceFileName,
            outputName = detail.outputName,
            extractType = extractType,
            parentFileObjectId = parentFileObjectId,
            parentDisplayPath = detail.parentDisplayPath,
            localFolderPath = detail.localFolderPath,
            openOnComplete = detail.openOnComplete,
            openOnCompleteHandled = detail.openOnCompleteHandled,
            outputAbsolutePath = detail.outputAbsolutePath,
            sourceAbsolutePath = detail.sourceAbsolutePath,
        )
    }

    suspend fun completeJob(operationId: Long, outputAbsolutePath: String) {
        val outputFile = File(outputAbsolutePath)
        database.withTransaction {
            extractOperationDao.updateOutputAbsolutePath(operationId, outputAbsolutePath)
            if (outputFile.isFile) {
                extractOperationDao.updateOutputName(operationId, outputFile.name)
            }
            operationDao.updateStatusAndWorkerId(
                id = operationId,
                status = OperationRepository.OperationStatus.COMPLETED.name,
                workerId = null,
            )
        }
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

    suspend fun markOpenOnCompleteHandled(operationId: Long) {
        extractOperationDao.markOpenOnCompleteHandled(operationId)
    }

    suspend fun disableOpenOnComplete(operationId: Long) {
        extractOperationDao.disableOpenOnComplete(operationId)
    }

    suspend fun getActiveJobIds(): List<Long> {
        return extractOperationDao.getActiveOperationIds()
    }

    suspend fun getPendingOpenOnCompleteJobIds(): List<Long> {
        return extractOperationDao.getPendingOpenOnCompleteOperationIds()
    }

    enum class ExtractType {
        Zip,
        TarGz,
        TarXz,
        TarZst,
        Zst,
        Xz,
    }

    data class ExtractJobMeta(
        val id: Long,
        val sourceFileObjectId: FileObjectId.Item?,
        val sourceFileName: String,
        val outputName: String,
        val extractType: ExtractType,
        val parentFileObjectId: FileObjectId?,
        val parentDisplayPath: String,
        val localFolderPath: String,
        val openOnComplete: Boolean,
        val openOnCompleteHandled: Boolean,
        val outputAbsolutePath: String?,
        val sourceAbsolutePath: String?,
    ) {
        val isExternalJob: Boolean
            get() = sourceAbsolutePath != null
    }

    data class NewExtractJob(
        val sourceFileObjectId: FileObjectId.Item,
        val sourceFileName: String,
        val outputName: String,
        val extractType: ExtractType,
        val parentFileObjectId: FileObjectId,
        val parentDisplayPath: String,
        val localFolderPath: String,
        val openOnComplete: Boolean,
    )

    data class NewExternalExtractJob(
        val sourceAbsolutePath: String,
        val sourceFileName: String,
        val outputName: String,
        val extractType: ExtractType,
        val outputParentPath: String,
        val openOnComplete: Boolean,
    )
}
