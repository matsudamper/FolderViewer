package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    suspend fun enqueueExtract(request: PendingExtractRequest): EnqueueExtractResult {
        return runCatching {
            val localFolderPath = dependencies.getLocalFolderPath() ?: return EnqueueExtractResult.Failure(
                "解凍はローカルストレージのみ対応しています",
            )
            val jobId = dependencies.extractJobRepository.createJob(
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
            EnqueueExtractResult.Success(jobId)
        }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    EnqueueExtractResult.Failure("解凍開始失敗: ${e.message}")
                }
            }
        }
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
                            showExtractDialogResult(
                                viewModelStateFlow = dependencies.viewModelStateFlow,
                                jobId = event.jobId,
                                message = event.message,
                            )
                        }
                    }

                    is ExtractJobCompletionWatcher.CompletionUiEvent.Failed -> {
                        if (dependencies.isExtractDialogOpenForJob(event.jobId)) {
                            showExtractDialogResult(
                                viewModelStateFlow = dependencies.viewModelStateFlow,
                                jobId = event.jobId,
                                message = event.message,
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
                if (event.parentFileObjectId != dependencies.fileObjectId) {
                    return@collect
                }
                val file = try {
                    dependencies.getRepository().getFiles(event.parentFileObjectId)
                        .find { it.id == event.fileId }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@collect
                } ?: return@collect
                dependencies.openWithExternalPlayer(file)
                dependencies.extractJobRepository.markOpenOnCompleteHandled(event.jobId)
            }
        }
    }

    internal data class Dependencies(
        val application: Application,
        val extractJobRepository: ExtractJobRepository,
        val operationRepository: OperationRepository,
        val selectionModeRepository: SelectionModeRepository,
        val fileObjectId: FileObjectId,
        val displayPath: String?,
        val getLocalFolderPath: () -> String?,
        val clearSelection: () -> Unit,
        val refreshFiles: suspend () -> Unit,
        val isExtractDialogOpenForJob: (Long) -> Boolean,
        val viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
        val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
        val getRepository: suspend () -> FileRepository,
        val openWithExternalPlayer: suspend (FileItem) -> Unit,
    )

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
