package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.SelectionModeRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil
import net.matsudamper.folderviewer.viewmodel.util.ExtractProgressText
import net.matsudamper.folderviewer.viewmodel.worker.FileExtractWorker

internal data class PendingExtractRequest(
    val fileItem: FileItem,
    val outputName: String,
    val extractType: ExtractableFileType,
)

internal class FileBrowserExtractCoordinator(
    private val dependencies: Dependencies,
) {
    private var progressJob: Job? = null
    private val dialogProgressFlow = MutableStateFlow<ExtractDialogProgress?>(null)
    val extractDialogProgress: StateFlow<ExtractDialogProgress?> = dialogProgressFlow.asStateFlow()

    fun startProgressObservation(scope: CoroutineScope, jobId: Long) {
        progressJob?.cancel()
        dialogProgressFlow.value = null
        progressJob = scope.launch {
            dependencies.operationRepository.observeProgressById(jobId).collect { progress ->
                if (progress == null) {
                    return@collect
                }
                dialogProgressFlow.value = ExtractDialogProgress(
                    progress = ExtractProgressText.progressRatio(progress),
                    progressText = ExtractProgressText.fromOperationProgress(progress),
                )
            }
        }
    }

    fun stopProgressObservation() {
        progressJob?.cancel()
        progressJob = null
        dialogProgressFlow.value = null
    }

    suspend fun createExtractJob(request: PendingExtractRequest): Long? {
        return runCatching {
            val localFolderPath = dependencies.getLocalFolderPath() ?: return null
            dependencies.extractJobRepository.createJob(
                ExtractJobRepository.NewExtractJob(
                    sourceFileObjectId = request.fileItem.id,
                    sourceFileName = request.fileItem.displayPath,
                    outputName = request.outputName,
                    extractType = request.extractType.toExtractJobType(),
                    parentFileObjectId = dependencies.fileObjectId,
                    parentDisplayPath = dependencies.displayPath.orEmpty(),
                    localFolderPath = localFolderPath,
                    openOnComplete = false,
                ),
            )
        }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    suspend fun startExtractJob(jobId: Long): Boolean {
        return runCatching {
            val inputData = Data.Builder()
                .putLong(FileExtractWorker.KEY_EXTRACT_OPERATION_ID, jobId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<FileExtractWorker>()
                .setInputData(inputData)
                .addTag(FileExtractWorker.TAG_EXTRACT)
                .build()
            dependencies.extractJobRepository.updateStatus(
                operationId = jobId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )
            WorkManager.getInstance(dependencies.application).enqueue(workRequest)
            dependencies.clearSelection()
            dependencies.selectionModeRepository.setSelectionMode(false)
            dependencies.extractJobCompletionWatcher.watchJob(jobId)
            true
        }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    suspend fun enqueueExtract(request: PendingExtractRequest): Long? {
        val jobId = createExtractJob(request) ?: return null
        if (!startExtractJob(jobId)) {
            return null
        }
        return jobId
    }

    fun observeCompletionEvents(scope: CoroutineScope) {
        scope.launch {
            dependencies.extractJobCompletionWatcher.completionUiEvents.collect { event ->
                val meta = dependencies.extractJobRepository.getJobMeta(event.jobId) ?: return@collect
                val parentFileObjectId = meta.parentFileObjectId ?: return@collect
                if (parentFileObjectId != dependencies.fileObjectId) {
                    return@collect
                }
                when (event) {
                    is ExtractJobCompletionWatcher.CompletionUiEvent.Completed -> {
                        dependencies.refreshFiles()
                        if (dependencies.isExtractDialogOpenForJob(event.jobId)) {
                            stopProgressObservation()
                            dependencies.updateExtractDialogOnComplete(
                                event.jobId,
                                event.message,
                            )
                        } else {
                            dependencies.uiChannelEvent.send(
                                FileBrowserUiEvent.ShowSnackbar(
                                    message = event.message,
                                    openExtractJobId = event.jobId,
                                ),
                            )
                        }
                    }

                    is ExtractJobCompletionWatcher.CompletionUiEvent.Failed -> {
                        if (dependencies.isExtractDialogOpenForJob(event.jobId)) {
                            stopProgressObservation()
                            dependencies.updateExtractDialogOnFailed(
                                event.jobId,
                                event.message,
                            )
                        } else {
                            dependencies.uiChannelEvent.send(
                                FileBrowserUiEvent.ShowSnackbar(
                                    message = event.message,
                                    extractDetailJobId = event.jobId,
                                ),
                            )
                        }
                    }

                    is ExtractJobCompletionWatcher.CompletionUiEvent.Cancelled -> {
                        if (dependencies.isExtractDialogOpenForJob(event.jobId)) {
                            stopProgressObservation()
                            dependencies.updateExtractDialogOnFailed(
                                event.jobId,
                                event.message,
                            )
                        } else {
                            dependencies.uiChannelEvent.send(
                                FileBrowserUiEvent.ShowSnackbar(
                                    message = event.message,
                                    extractDetailJobId = event.jobId,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun observeExternalOpenEvents(scope: CoroutineScope) {
        scope.launch {
            dependencies.extractJobCompletionWatcher.pendingExternalOpen.collect { event ->
                val parentFileObjectId = event.parentFileObjectId ?: return@collect
                if (parentFileObjectId != dependencies.fileObjectId) {
                    return@collect
                }
                val directOpen = event.directOpen
                if (directOpen != null) {
                    dependencies.openFileFromUri(
                        directOpen.viewSourceUri,
                        directOpen.fileName,
                        directOpen.mimeType,
                    )
                    dependencies.extractJobRepository.markOpenOnCompleteHandled(event.jobId)
                    return@collect
                }
                val fileId = event.fileId ?: return@collect
                val file = try {
                    dependencies.getRepository().getFiles(parentFileObjectId)
                        .find { it.id == fileId }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@collect
                } ?: return@collect
                dependencies.openWithExternalPlayer(file)
                dependencies.extractJobRepository.markOpenOnCompleteHandled(event.jobId)
            }
        }
        scope.launch {
            dependencies.extractJobCompletionWatcher.pendingExternalFolderOpen.collect { event ->
                val parentFileObjectId = event.parentFileObjectId ?: return@collect
                if (parentFileObjectId != dependencies.fileObjectId) {
                    return@collect
                }
                dependencies.openOutputFolder(event.absolutePath)
                dependencies.extractJobRepository.markOpenOnCompleteHandled(event.jobId)
            }
        }
    }

    internal data class Dependencies(
        val application: Application,
        val extractJobRepository: ExtractJobRepository,
        val operationRepository: OperationRepository,
        val selectionModeRepository: SelectionModeRepository,
        val uiChannelEvent: Channel<FileBrowserUiEvent>,
        val fileObjectId: FileObjectId,
        val displayPath: String?,
        val getLocalFolderPath: () -> String?,
        val clearSelection: () -> Unit,
        val refreshFiles: suspend () -> Unit,
        val isExtractDialogOpenForJob: (Long) -> Boolean,
        val updateExtractDialogOnComplete: (Long, String) -> Unit,
        val updateExtractDialogOnFailed: (Long, String) -> Unit,
        val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
        val getRepository: suspend () -> FileRepository,
        val openWithExternalPlayer: suspend (FileItem) -> Unit,
        val openFileFromUri: suspend (ViewSourceUri, String, String?) -> Unit,
        val openOutputFolder: suspend (String) -> Unit,
    )

    suspend fun openExtractResult(jobId: Long): Boolean {
        return dependencies.extractJobCompletionWatcher.openExtractResult(jobId)
    }

    private fun ExtractableFileType.toExtractJobType(): ExtractJobRepository.ExtractType {
        return when (this) {
            ExtractableFileType.Zip -> ExtractJobRepository.ExtractType.Zip

            ExtractableFileType.TarGz -> ExtractJobRepository.ExtractType.TarGz

            ExtractableFileType.TarXz -> ExtractJobRepository.ExtractType.TarXz

            ExtractableFileType.TarZst -> ExtractJobRepository.ExtractType.TarZst

            is ExtractableFileType.Compressed -> when (format) {
                CompressedFileUtil.Format.Zst -> ExtractJobRepository.ExtractType.Zst
                CompressedFileUtil.Format.Xz -> ExtractJobRepository.ExtractType.Xz
            }
        }
    }
}

internal data class ExtractDialogProgress(
    val progress: Float?,
    val progressText: String?,
)
