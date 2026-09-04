package net.matsudamper.folderviewer.viewmodel.extract

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.extract.ExternalExtractUiState
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputLocationResolver
import net.matsudamper.folderviewer.viewmodel.util.ExtractProgressText
import net.matsudamper.folderviewer.viewmodel.util.ExtractableFileNameUtil
import net.matsudamper.folderviewer.viewmodel.worker.FileExtractWorker

@HiltViewModel(assistedFactory = ExternalExtractViewModel.Companion.Factory::class)
class ExternalExtractViewModel @AssistedInject constructor(
    @Assisted private val args: ExternalExtractLaunchArgs,
    private val extractJobRepository: ExtractJobRepository,
    private val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
    private val operationRepository: OperationRepository,
    private val storageRepository: StorageRepository,
    application: Application,
) : AndroidViewModel(application) {

    private var extractProgressJob: Job? = null
    private var activeJobId: Long? = null

    private val callbacks = object : ExternalExtractUiState.Callbacks {
        override fun onDismissRequest() {
            viewModelEventChannel.trySend(ViewModelEvent.Finish)
        }

        override fun onConfirm(outputName: String) {
            viewModelScope.launch {
                enqueueExtract(outputName.trim())
            }
        }

        override fun onOpenResult() {
            val jobId = activeJobId ?: return
            viewModelScope.launch {
                openExtractOutput(jobId)
            }
        }

        override fun onOpenDetail() {
            val jobId = activeJobId ?: return
            viewModelEventChannel.trySend(ViewModelEvent.OpenExtractDetail(jobId))
        }
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<ExternalExtractUiState> = _uiState.asStateFlow()

    private val viewModelEventChannel = Channel<ViewModelEvent>(capacity = 1)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private fun createInitialState(): ExternalExtractUiState {
        return ExternalExtractUiState(
            defaultName = ExtractableFileNameUtil.defaultOutputName(args.fileName, args.extractType.toExtractableType()),
            mode = args.extractType.toExtractDialogMode(),
            isExtracting = false,
            isExtractComplete = false,
            statusMessage = null,
            locationMessage = args.locationMessage,
            callbacks = callbacks,
        )
    }

    private suspend fun enqueueExtract(outputName: String) {
        if (outputName.isBlank()) {
            return
        }
        _uiState.value = _uiState.value.copy(
            isExtracting = true,
            isExtractComplete = false,
            statusMessage = null,
        )
        val jobId = runCatching {
            val operationId = extractJobRepository.createExternalJob(
                ExtractJobRepository.NewExternalExtractJob(
                    sourceAbsolutePath = args.sourcePath,
                    sourceFileName = args.fileName,
                    outputName = outputName,
                    extractType = args.extractType.toExtractJobType(),
                    outputParentPath = args.outputParentPath,
                    openOnComplete = false,
                ),
            )
            activeJobId = operationId
            val inputData = Data.Builder()
                .putLong(FileExtractWorker.KEY_EXTRACT_OPERATION_ID, operationId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<FileExtractWorker>()
                .setInputData(inputData)
                .addTag(FileExtractWorker.TAG_EXTRACT)
                .build()
            extractJobRepository.updateStatus(
                operationId = operationId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )
            WorkManager.getInstance(getApplication()).enqueue(workRequest)
            observeJobCompletion(operationId)
            startExtractProgressObservation(operationId)
            extractJobCompletionWatcher.watchJob(operationId)
            operationId
        }.getOrElse { e ->
            activeJobId = null
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                isExtractComplete = false,
                statusMessage = "解凍開始失敗: ${e.message}",
            )
            return
        }
        activeJobId = jobId
    }

    private fun startExtractProgressObservation(jobId: Long) {
        extractProgressJob?.cancel()
        extractProgressJob = viewModelScope.launch {
            operationRepository.observeProgressById(jobId).collect { progress ->
                if (progress == null) {
                    return@collect
                }
                _uiState.value = _uiState.value.copy(
                    progress = ExtractProgressText.progressRatio(progress),
                    progressText = ExtractProgressText.fromOperationProgress(progress),
                )
            }
        }
    }

    private fun stopExtractProgressObservation() {
        extractProgressJob?.cancel()
        extractProgressJob = null
    }

    private fun observeJobCompletion(jobId: Long) {
        viewModelScope.launch {
            val event = extractJobCompletionWatcher.completionUiEvents
                .filter { it.jobId == jobId }
                .first()
            stopExtractProgressObservation()
            when (event) {
                is ExtractJobCompletionWatcher.CompletionUiEvent.Completed -> {
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        isExtractComplete = true,
                        statusMessage = event.message,
                    )
                }

                is ExtractJobCompletionWatcher.CompletionUiEvent.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        isExtractComplete = false,
                        statusMessage = event.message,
                    )
                }

                is ExtractJobCompletionWatcher.CompletionUiEvent.Cancelled -> {
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        isExtractComplete = false,
                        statusMessage = event.message,
                    )
                }
            }
        }
    }

    private suspend fun openExtractOutput(jobId: Long) {
        val meta = extractJobRepository.getJobMeta(jobId) ?: return
        ExtractOutputLocationResolver.resolveNavigateToOutput(meta, storageRepository)?.let { target ->
            viewModelEventChannel.send(
                ViewModelEvent.NavigateToFileBrowser(
                    fileId = target.fileId,
                    displayPath = target.displayPath,
                ),
            )
            return
        }
        ExtractOutputLocationResolver.resolveOpenOutputFile(meta, storageRepository)?.let { target ->
            viewModelEventChannel.send(
                ViewModelEvent.OpenOutputFile(
                    viewSourceUri = target.viewSourceUri,
                    fileName = target.fileName,
                    mimeType = target.mimeType,
                ),
            )
            return
        }
        ExtractOutputLocationResolver.resolveOpenOutputFolderPath(meta)?.let { target ->
            viewModelEventChannel.send(ViewModelEvent.OpenOutputFolder(target.absolutePath))
            return
        }
        _uiState.value = _uiState.value.copy(
            statusMessage = "解凍結果を開けませんでした",
        )
    }

    sealed interface ViewModelEvent {
        data object Finish : ViewModelEvent

        data class OpenExtractDetail(
            val jobId: Long,
        ) : ViewModelEvent

        data class NavigateToFileBrowser(
            val fileId: FileObjectId,
            val displayPath: String?,
        ) : ViewModelEvent

        data class OpenOutputFile(
            val viewSourceUri: ViewSourceUri,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent

        data class OpenOutputFolder(
            val absolutePath: String,
        ) : ViewModelEvent
    }

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(args: ExternalExtractLaunchArgs): ExternalExtractViewModel
        }
    }
}

private fun ExtractLaunchType.toExtractDialogMode(): ExtractDialogMode {
    return when (this) {
        ExtractLaunchType.Zip,
        ExtractLaunchType.TarGz,
        ExtractLaunchType.TarXz,
        ExtractLaunchType.TarZst,
        -> ExtractDialogMode.ZipFolder

        ExtractLaunchType.Zst -> ExtractDialogMode.ZstFile

        ExtractLaunchType.Xz -> ExtractDialogMode.XzFile
    }
}

private fun ExtractLaunchType.toExtractJobType(): ExtractJobRepository.ExtractType {
    return when (this) {
        ExtractLaunchType.Zip -> ExtractJobRepository.ExtractType.Zip
        ExtractLaunchType.TarGz -> ExtractJobRepository.ExtractType.TarGz
        ExtractLaunchType.TarXz -> ExtractJobRepository.ExtractType.TarXz
        ExtractLaunchType.TarZst -> ExtractJobRepository.ExtractType.TarZst
        ExtractLaunchType.Zst -> ExtractJobRepository.ExtractType.Zst
        ExtractLaunchType.Xz -> ExtractJobRepository.ExtractType.Xz
    }
}

private fun ExtractLaunchType.toExtractableType(): net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType {
    return when (this) {
        ExtractLaunchType.Zip -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Zip

        ExtractLaunchType.TarGz -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarGz

        ExtractLaunchType.TarXz -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarXz

        ExtractLaunchType.TarZst -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarZst

        ExtractLaunchType.Zst -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Compressed(
            net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil.Format.Zst,
        )

        ExtractLaunchType.Xz -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Compressed(
            net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil.Format.Xz,
        )
    }
}
