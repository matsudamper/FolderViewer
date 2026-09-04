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
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.extract.ExternalExtractUiState
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher
import net.matsudamper.folderviewer.viewmodel.util.ExtractProgressText
import net.matsudamper.folderviewer.viewmodel.util.ExtractableFileNameUtil
import net.matsudamper.folderviewer.viewmodel.worker.FileExtractWorker

@HiltViewModel(assistedFactory = ExternalExtractViewModel.Companion.Factory::class)
class ExternalExtractViewModel @AssistedInject constructor(
    @Assisted private val args: ExternalExtractLaunchArgs,
    private val extractJobRepository: ExtractJobRepository,
    private val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
    private val operationRepository: OperationRepository,
    application: Application,
) : AndroidViewModel(application) {

    private var extractProgressJob: Job? = null

    private val callbacks = object : ExternalExtractUiState.Callbacks {
        override fun onDismissRequest() {
            viewModelEventChannel.trySend(ViewModelEvent.Finish)
        }

        override fun onConfirm(outputName: String) {
            viewModelScope.launch {
                enqueueExtract(outputName.trim())
            }
        }

        override fun onOpenResult() = Unit
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
        _uiState.value = _uiState.value.copy(isExtracting = true)
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
            extractJobCompletionWatcher.watchJob(operationId)
            observeJobFailure(operationId)
            startExtractProgressObservation(operationId)
            operationId
        }.getOrElse { e ->
            _uiState.value = _uiState.value.copy(isExtracting = false)
            viewModelEventChannel.send(
                ViewModelEvent.ShowSnackbar(
                    message = "解凍開始失敗: ${e.message}",
                ),
            )
            return
        }
        viewModelEventChannel.send(
            ViewModelEvent.ShowSnackbar(
                message = "解凍を開始しました",
                extractDetailJobId = jobId,
                finishAfterDismiss = true,
            ),
        )
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

    private fun observeJobFailure(jobId: Long) {
        viewModelScope.launch {
            val event = extractJobCompletionWatcher.completionUiEvents
                .filter { it.jobId == jobId }
                .first()
            if (event is ExtractJobCompletionWatcher.CompletionUiEvent.Failed) {
                _uiState.value = _uiState.value.copy(isExtracting = false)
                viewModelEventChannel.send(
                    ViewModelEvent.ShowSnackbar(
                        message = event.message,
                        extractDetailJobId = jobId,
                    ),
                )
            }
        }
    }

    sealed interface ViewModelEvent {
        data object Finish : ViewModelEvent

        data class ShowSnackbar(
            val message: String,
            val extractDetailJobId: Long? = null,
            val finishAfterDismiss: Boolean = false,
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
