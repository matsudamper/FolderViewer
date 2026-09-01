package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
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
    val openOnComplete: Boolean,
)

internal class FileBrowserExtractCoordinator(
    private val dependencies: Dependencies,
) {
    suspend fun enqueueExtract(request: PendingExtractRequest) {
        runCatching {
            val localFolderPath = dependencies.getLocalFolderPath() ?: run {
                dependencies.uiChannelEvent.send(
                    FileBrowserUiEvent.ShowSnackbar("解凍はローカルストレージのみ対応しています"),
                )
                return
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
                    openOnComplete = request.openOnComplete,
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
            observeExtractJobCompletion(jobId, request.openOnComplete)
            dependencies.uiChannelEvent.send(
                FileBrowserUiEvent.ShowSnackbar("解凍を開始しました", showAction = true),
            )
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    e.printStackTrace()
                    dependencies.uiChannelEvent.trySend(
                        FileBrowserUiEvent.ShowSnackbar("解凍開始失敗: ${e.message}"),
                    )
                }
            }
        }
    }

    fun observeExtractJobCompletion(jobId: Long, openOnComplete: Boolean) {
        dependencies.viewModelScope.launch {
            val terminalStatus = dependencies.operationRepository.observeProgressById(jobId)
                .mapNotNull { it?.status }
                .first { status ->
                    status == OperationRepository.OperationStatus.COMPLETED ||
                        status == OperationRepository.OperationStatus.FAILED ||
                        status == OperationRepository.OperationStatus.CANCELLED
                }
            when (terminalStatus) {
                OperationRepository.OperationStatus.COMPLETED -> {
                    dependencies.refreshFiles()
                    val meta = dependencies.extractJobRepository.getJobMeta(jobId)
                    val message = when (meta?.extractType) {
                        ExtractJobRepository.ExtractType.Zip -> "${meta.outputName}に展開しました"
                        ExtractJobRepository.ExtractType.Zst,
                        ExtractJobRepository.ExtractType.Xz,
                        -> "${meta.outputName}を作成しました"
                        null -> "解凍が完了しました"
                    }
                    dependencies.uiChannelEvent.send(
                        FileBrowserUiEvent.ShowSnackbar(
                            message = message,
                            openExtractJobId = jobId,
                        ),
                    )
                    if (openOnComplete) {
                        openExtractResult(jobId)
                    }
                }

                OperationRepository.OperationStatus.FAILED -> {
                    val progress = dependencies.operationRepository.observeProgressById(jobId)
                        .mapNotNull { it }
                        .first()
                    dependencies.uiChannelEvent.send(
                        FileBrowserUiEvent.ShowSnackbar(
                            message = progress.errorMessage ?: "解凍に失敗しました",
                        ),
                    )
                }

                else -> Unit
            }
        }
    }

    suspend fun openExtractResult(jobId: Long) {
        val meta = dependencies.extractJobRepository.getJobMeta(jobId) ?: return
        val repo = dependencies.getRepository()
        when (meta.extractType) {
            ExtractJobRepository.ExtractType.Zip -> {
                val folder = repo.getFiles(meta.parentFileObjectId)
                    .find { it.isDirectory && it.displayPath == meta.outputName }
                    ?: return
                val newDisplayPath = if (meta.parentDisplayPath.isEmpty()) {
                    folder.displayPath
                } else {
                    "${meta.parentDisplayPath}/${folder.displayPath}"
                }
                dependencies.viewModelEventChannel.send(
                    FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser(
                        displayPath = newDisplayPath,
                        id = folder.id,
                    ),
                )
            }

            ExtractJobRepository.ExtractType.Zst,
            ExtractJobRepository.ExtractType.Xz,
            -> {
                val file = repo.getFiles(meta.parentFileObjectId)
                    .find { !it.isDirectory && it.displayPath == meta.outputName }
                    ?: return
                dependencies.openWithExternalPlayer(file)
            }
        }
    }

    internal data class Dependencies(
        val application: Application,
        val extractJobRepository: ExtractJobRepository,
        val operationRepository: OperationRepository,
        val selectionModeRepository: SelectionModeRepository,
        val viewModelScope: CoroutineScope,
        val uiChannelEvent: Channel<FileBrowserUiEvent>,
        val viewModelEventChannel: Channel<FileBrowserViewModel.ViewModelEvent>,
        val fileObjectId: FileObjectId,
        val displayPath: String?,
        val getLocalFolderPath: () -> String?,
        val clearSelection: () -> Unit,
        val refreshFiles: suspend () -> Unit,
        val getRepository: suspend () -> FileRepository,
        val openWithExternalPlayer: suspend (FileItem) -> Unit,
    )

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
