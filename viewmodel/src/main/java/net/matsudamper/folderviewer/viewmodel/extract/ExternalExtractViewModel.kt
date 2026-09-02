package net.matsudamper.folderviewer.viewmodel.extract

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.extract.ExternalExtractUiState
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher
import net.matsudamper.folderviewer.viewmodel.util.ExtractableFileNameUtil
import net.matsudamper.folderviewer.viewmodel.worker.FileExtractWorker

@HiltViewModel(assistedFactory = ExternalExtractViewModel.Companion.Factory::class)
class ExternalExtractViewModel @AssistedInject constructor(
    @Assisted private val args: ExternalExtractLaunchArgs,
    private val extractJobRepository: ExtractJobRepository,
    private val extractJobCompletionWatcher: ExtractJobCompletionWatcher,
    application: Application,
) : AndroidViewModel(application) {

    private val callbacks = object : ExternalExtractUiState.Callbacks {
        override fun onDismissRequest() {
            viewModelEventChannel.trySend(ViewModelEvent.Finish)
        }

        override fun onConfirm(outputName: String) {
            viewModelScope.launch {
                enqueueExtract(outputName.trim())
            }
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
            locationMessage = args.locationMessage,
            callbacks = callbacks,
        )
    }

    private suspend fun enqueueExtract(outputName: String) {
        if (outputName.isBlank()) {
            return
        }
        _uiState.value = _uiState.value.copy(isExtracting = true)
        runCatching {
            val jobId = extractJobRepository.createExternalJob(
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
                .putLong(FileExtractWorker.KEY_EXTRACT_OPERATION_ID, jobId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<FileExtractWorker>()
                .setInputData(inputData)
                .addTag(FileExtractWorker.TAG_EXTRACT)
                .build()
            extractJobRepository.updateStatus(
                operationId = jobId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )
            WorkManager.getInstance(getApplication()).enqueue(workRequest)
            extractJobCompletionWatcher.watchJob(jobId)
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(isExtracting = false)
            viewModelEventChannel.send(ViewModelEvent.ShowMessage("解凍開始失敗: ${e.message}"))
            return
        }
        viewModelEventChannel.send(ViewModelEvent.FinishWithMessage("解凍を開始しました"))
    }

    sealed interface ViewModelEvent {
        data object Finish : ViewModelEvent

        data class FinishWithMessage(val message: String) : ViewModelEvent

        data class ShowMessage(val message: String) : ViewModelEvent
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
        ExtractLaunchType.TarXz -> ExtractJobRepository.ExtractType.TarXz
        ExtractLaunchType.TarZst -> ExtractJobRepository.ExtractType.TarZst
        ExtractLaunchType.Zst -> ExtractJobRepository.ExtractType.Zst
        ExtractLaunchType.Xz -> ExtractJobRepository.ExtractType.Xz
    }
}

private fun ExtractLaunchType.toExtractableType(): net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType {
    return when (this) {
        ExtractLaunchType.Zip -> net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Zip
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
