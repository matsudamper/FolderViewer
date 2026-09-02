package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.SelectionModeRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil
import net.matsudamper.folderviewer.viewmodel.worker.FileExtractWorker

internal data class PendingExtractRequest(
    val fileItem: FileItem,
    val outputName: String,
    val extractType: ExtractableFileType,
)

internal class FileBrowserExtractCoordinator(
    private val dependencies: Dependencies,
) {
    suspend fun enqueueExtract(request: PendingExtractRequest): Long? {
        return runCatching {
            val localFolderPath = dependencies.getLocalFolderPath() ?: run {
                dependencies.uiChannelEvent.send(
                    FileBrowserUiEvent.ShowSnackbar("解凍はローカルストレージのみ対応しています"),
                )
                return null
            }
            val jobId = dependencies.extractJobRepository.createJob(
                ExtractJobRepository.NewExtractJob(
                    sourceFileObjectId = request.fileItem.id,
                    sourceFileName = request.fileItem.displayPath,
                    outputName = request.outputName,
                    extractType = request.extractType.toExtractJobType(),
                    parentFileObjectId = dependencies.fileObjectId,
                    parentDisplayPath = dependencies.displayPath.orEmpty(),
                    localFolderPath = localFolderPath,
                    openOnComplete = true,
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
            jobId
        }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    e.printStackTrace()
                    dependencies.uiChannelEvent.trySend(
                        FileBrowserUiEvent.ShowSnackbar("解凍開始失敗: ${e.message}"),
                    )
                    null
                }
            }
        }
    }

    fun observeCompletionEvents(scope: CoroutineScope) {
        scope.launch {
            dependencies.extractJobCompletionWatcher.completionUiEvents.collect { event ->
                val meta = dependencies.extractJobRepository.getJobMeta(event.jobId) ?: return@collect
                if (meta.parentFileObjectId != dependencies.fileObjectId) {
                    return@collect
                }
                when (event) {
                    is ExtractJobCompletionWatcher.CompletionUiEvent.Completed -> {
                        dependencies.refreshFiles()
                        if (dependencies.isExtractDialogOpenForJob(event.jobId)) {
                            dependencies.closeExtractDialog()
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
                            dependencies.closeExtractDialog()
                        }
                        dependencies.uiChannelEvent.send(
                            FileBrowserUiEvent.ShowSnackbar(message = event.message),
                        )
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
                    dependencies.uiChannelEvent.trySend(
                        FileBrowserUiEvent.ShowSnackbar("解凍結果を開けませんでした: ${e.message}"),
                    )
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
        val uiChannelEvent: Channel<FileBrowserUiEvent>,
        val fileObjectId: FileObjectId,
        val displayPath: String?,
        val getLocalFolderPath: () -> String?,
        val clearSelection: () -> Unit,
        val refreshFiles: suspend () -> Unit,
        val isExtractDialogOpenForJob: (Long) -> Boolean,
        val closeExtractDialog: () -> Unit,
        val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
        val getRepository: suspend () -> FileRepository,
        val openWithExternalPlayer: suspend (FileItem) -> Unit,
    )

    suspend fun openExtractResult(jobId: Long) {
        dependencies.extractJobCompletionWatcher.openExtractResult(jobId)
    }

    private fun ExtractableFileType.toExtractJobType(): ExtractJobRepository.ExtractType {
        return when (this) {
            ExtractableFileType.Zip -> ExtractJobRepository.ExtractType.Zip
            is ExtractableFileType.Compressed -> when (format) {
                CompressedFileUtil.Format.Zst -> ExtractJobRepository.ExtractType.Zst
                CompressedFileUtil.Format.Xz -> ExtractJobRepository.ExtractType.Xz
            }
        }
    }
}
