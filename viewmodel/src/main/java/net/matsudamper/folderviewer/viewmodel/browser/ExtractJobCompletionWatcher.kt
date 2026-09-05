package net.matsudamper.folderviewer.viewmodel.browser

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputLocationResolver

@Singleton
class ExtractJobCompletionWatcher @Inject constructor(
    private val operationRepository: OperationRepository,
    private val extractJobRepository: ExtractJobRepository,
    private val storageRepository: StorageRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeWatches = ConcurrentHashMap.newKeySet<Long>()

    private val _completionUiEvents = MutableSharedFlow<CompletionUiEvent>(extraBufferCapacity = 8)
    val completionUiEvents = _completionUiEvents.asSharedFlow()

    private val _pendingNavigation = MutableSharedFlow<PendingExtractNavigation>(extraBufferCapacity = 4)
    val pendingNavigation = _pendingNavigation.asSharedFlow()

    private val _pendingExternalOpen = MutableSharedFlow<PendingExtractExternalOpen>(extraBufferCapacity = 4)
    val pendingExternalOpen = _pendingExternalOpen.asSharedFlow()

    private val _pendingExternalFolderOpen = MutableSharedFlow<PendingExtractExternalFolderOpen>(extraBufferCapacity = 4)
    val pendingExternalFolderOpen = _pendingExternalFolderOpen.asSharedFlow()

    fun watchJob(jobId: Long) {
        if (!activeWatches.add(jobId)) {
            return
        }
        scope.launch {
            try {
                val terminalStatus = operationRepository.observeProgressById(jobId)
                    .mapNotNull { it?.status }
                    .first { status ->
                        status == OperationRepository.OperationStatus.COMPLETED ||
                            status == OperationRepository.OperationStatus.FAILED ||
                            status == OperationRepository.OperationStatus.CANCELLED
                    }
                handleTerminalStatus(jobId, terminalStatus)
            } finally {
                activeWatches.remove(jobId)
            }
        }
    }

    fun resume() {
        scope.launch {
            val activeJobIds = extractJobRepository.getActiveJobIds()
            activeJobIds.forEach { watchJob(it) }
            val pendingOpenJobIds = extractJobRepository.getPendingOpenOnCompleteJobIds()
            pendingOpenJobIds.forEach { jobId ->
                handleOpenOnComplete(jobId)
            }
        }
    }

    suspend fun openExtractResult(jobId: Long): Boolean {
        val meta = extractJobRepository.getJobMeta(jobId) ?: return false
        return when (
            val result = ExtractOutputLocationResolver.resolveOpenExtractResult(
                meta = meta,
                storageRepository = storageRepository,
            )
        ) {
            is ExtractOutputLocationResolver.OpenExtractResult.OpenFile -> {
                emitOpenFile(jobId, meta, result.target)
            }

            is ExtractOutputLocationResolver.OpenExtractResult.NavigateToOutput -> {
                _pendingNavigation.emit(
                    PendingExtractNavigation(
                        jobId = jobId,
                        displayPath = result.target.displayPath,
                        fileId = result.target.fileId,
                    ),
                )
                true
            }

            is ExtractOutputLocationResolver.OpenExtractResult.OpenFolder -> {
                _pendingExternalFolderOpen.emit(
                    PendingExtractExternalFolderOpen(
                        jobId = jobId,
                        parentFileObjectId = meta.parentFileObjectId,
                        absolutePath = result.target.absolutePath,
                    ),
                )
                true
            }

            null -> false
        }
    }

    private suspend fun handleTerminalStatus(
        jobId: Long,
        terminalStatus: OperationRepository.OperationStatus,
    ) {
        when (terminalStatus) {
            OperationRepository.OperationStatus.COMPLETED -> {
                val meta = extractJobRepository.getJobMeta(jobId) ?: return
                val outputFile = meta.outputAbsolutePath?.let { File(it) }
                val message = if (outputFile != null && outputFile.isDirectory) {
                    "${meta.outputName}に展開しました"
                } else {
                    val outputLabel = outputFile?.name ?: meta.outputName
                    "${outputLabel}を作成しました"
                }
                _completionUiEvents.emit(
                    CompletionUiEvent.Completed(
                        jobId = jobId,
                        message = message,
                    ),
                )
                if (meta.openOnComplete) {
                    handleOpenOnComplete(jobId)
                }
            }

            OperationRepository.OperationStatus.FAILED -> {
                val progress = operationRepository.observeProgressById(jobId)
                    .mapNotNull { it }
                    .first()
                _completionUiEvents.emit(
                    CompletionUiEvent.Failed(
                        jobId = jobId,
                        message = progress.errorMessage ?: "解凍に失敗しました",
                    ),
                )
            }

            OperationRepository.OperationStatus.CANCELLED -> {
                _completionUiEvents.emit(
                    CompletionUiEvent.Cancelled(
                        jobId = jobId,
                        message = "解凍がキャンセルされました",
                    ),
                )
            }

            else -> Unit
        }
    }

    private suspend fun handleOpenOnComplete(jobId: Long) {
        val meta = extractJobRepository.getJobMeta(jobId) ?: return
        if (!meta.openOnComplete || meta.openOnCompleteHandled) {
            return
        }
        openExtractResult(jobId)
    }

    private suspend fun emitOpenFile(
        jobId: Long,
        meta: ExtractJobRepository.ExtractJobMeta,
        target: ExtractOutputLocationResolver.OpenFileTarget,
    ): Boolean {
        if (!meta.isExternalJob) {
            val parentFileObjectId = meta.parentFileObjectId
            val repository = parentFileObjectId?.let {
                storageRepository.getFileRepository(it.storageId)
            }
            if (parentFileObjectId != null && repository != null) {
                val file = repository.getFiles(parentFileObjectId)
                    .find { !it.isDirectory && it.displayPath == meta.outputName }
                if (file != null) {
                    _pendingExternalOpen.emit(
                        PendingExtractExternalOpen(
                            jobId = jobId,
                            parentFileObjectId = parentFileObjectId,
                            fileId = file.id,
                        ),
                    )
                    return true
                }
            }
        }
        _pendingExternalOpen.emit(
            PendingExtractExternalOpen(
                jobId = jobId,
                parentFileObjectId = meta.parentFileObjectId,
                fileId = null,
                directOpen = PendingExtractExternalOpen.DirectOpen(
                    viewSourceUri = target.viewSourceUri,
                    fileName = target.fileName,
                    mimeType = target.mimeType,
                ),
            ),
        )
        return true
    }

    sealed interface CompletionUiEvent {
        val jobId: Long
        val message: String

        data class Completed(
            override val jobId: Long,
            override val message: String,
        ) : CompletionUiEvent

        data class Failed(
            override val jobId: Long,
            override val message: String,
        ) : CompletionUiEvent

        data class Cancelled(
            override val jobId: Long,
            override val message: String,
        ) : CompletionUiEvent
    }

    data class PendingExtractNavigation(
        val jobId: Long,
        val displayPath: String?,
        val fileId: FileObjectId,
    )

    data class PendingExtractExternalOpen(
        val jobId: Long,
        val parentFileObjectId: FileObjectId?,
        val fileId: FileObjectId?,
        val directOpen: DirectOpen? = null,
    ) {
        data class DirectOpen(
            val viewSourceUri: ViewSourceUri,
            val fileName: String,
            val mimeType: String?,
        )
    }

    data class PendingExtractExternalFolderOpen(
        val jobId: Long,
        val parentFileObjectId: FileObjectId?,
        val absolutePath: String,
    )
}
