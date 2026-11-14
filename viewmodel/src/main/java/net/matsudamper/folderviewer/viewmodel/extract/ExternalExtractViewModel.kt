package net.matsudamper.folderviewer.viewmodel.extract

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import net.matsudamper.folderviewer.ui.browser.ExtractDialogUiState
import net.matsudamper.folderviewer.ui.extract.ExternalExtractUiState
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher
import net.matsudamper.folderviewer.viewmodel.util.ExternalExtractStagingSupport
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
    private var deleteSourceRequested = false

    private val callbacks = object : ExtractDialogUiState.Callbacks {
        override fun onDismissRequest() {
            val state = viewModelStateFlow.value
            if (state.isResultActionInProgress) {
                return
            }
            if (state.isExtracting) {
                viewModelEventChannel.trySend(ViewModelEvent.Finish)
                return
            }
            viewModelScope.launch {
                deleteStagedSourceIfNeeded()
                viewModelEventChannel.send(ViewModelEvent.Finish)
            }
        }

        override fun onClose() {
            if (viewModelStateFlow.value.isExtracting) {
                viewModelEventChannel.trySend(ViewModelEvent.Finish)
                return
            }
            startResultAction {
                deleteStagedSourceIfNeeded()
                viewModelEventChannel.send(ViewModelEvent.Finish)
                true
            }
        }

        override fun onConfirm(outputName: String) {
            viewModelScope.launch {
                enqueueExtract(outputName.trim())
            }
        }

        override fun onOpenResult() {
            val jobId = activeJobId ?: return
            startResultAction {
                openExtractOutput(jobId)
            }
        }

        override fun onOpenDetail() {
            val jobId = activeJobId ?: return
            startResultAction {
                viewModelEventChannel.send(ViewModelEvent.OpenExtractDetail(jobId))
                true
            }
        }

        override fun onDeleteSourceRequested() {
            deleteSourceRequested = true
        }

        override fun onStatusMessageLinkClick() {
            onOpenResult()
        }
    }

    private val viewModelStateFlow = MutableStateFlow(createInitialState())
    val uiState: StateFlow<ExternalExtractUiState> = viewModelStateFlow
        .map { createUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, createUiState(viewModelStateFlow.value))

    private val viewModelEventChannel = Channel<ViewModelEvent>(capacity = 1)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private fun createInitialState(): ViewModelState {
        return ViewModelState(
            defaultName = ExtractableFileNameUtil.defaultOutputName(args.fileName, args.extractType.toExtractableType()),
            isExtracting = false,
            isExtractComplete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            isResultActionInProgress = false,
            progress = null,
            progressText = null,
        )
    }

    private fun createUiState(state: ViewModelState): ExternalExtractUiState {
        val resultButtonsVisible = state.isExtracting || state.isExtractComplete
        return ExternalExtractUiState(
            dialog = ExtractDialogUiState(
                defaultName = state.defaultName,
                mode = args.extractType.toExtractDialogMode(),
                hintMessage = args.locationMessage,
                nameInputVisible = !resultButtonsVisible,
                progressVisible = state.isExtracting,
                progress = state.progress,
                progressText = state.progressText,
                statusMessage = state.statusMessage,
                statusMessageLinkText = state.statusMessageLinkText,
                deleteSourceCheckboxVisible = state.isExtractComplete && canDeleteSource(),
                resultButtonsVisible = resultButtonsVisible,
                resultButtonsEnabled = !state.isResultActionInProgress,
                openButtonEnabled = state.isExtractComplete && !state.isResultActionInProgress,
                callbacks = callbacks,
            ),
        )
    }

    private fun canDeleteSource(): Boolean {
        return when (Uri.parse(args.sourceUri).scheme) {
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE,
            -> true

            else -> false
        }
    }

    private fun startResultAction(action: suspend () -> Boolean) {
        if (viewModelStateFlow.value.isResultActionInProgress) {
            return
        }
        viewModelStateFlow.value = viewModelStateFlow.value.copy(isResultActionInProgress = true)
        viewModelScope.launch {
            val completed = try {
                if (!deleteSourceIfRequested()) {
                    false
                } else {
                    action()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                viewModelStateFlow.value = viewModelStateFlow.value.copy(
                    statusMessage = e.message ?: "操作を完了できませんでした",
                    statusMessageLinkText = null,
                )
                false
            }
            if (!completed) {
                viewModelStateFlow.value = viewModelStateFlow.value.copy(isResultActionInProgress = false)
            }
        }
    }

    private suspend fun enqueueExtract(outputName: String) {
        if (outputName.isBlank()) {
            return
        }
        viewModelStateFlow.value = viewModelStateFlow.value.copy(
            defaultName = outputName,
            isExtracting = true,
            isExtractComplete = false,
            statusMessage = null,
            statusMessageLinkText = null,
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
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
            viewModelStateFlow.value = viewModelStateFlow.value.copy(
                isExtracting = false,
                isExtractComplete = false,
                statusMessage = "解凍開始失敗: ${e.message}",
                statusMessageLinkText = null,
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
                viewModelStateFlow.value = viewModelStateFlow.value.copy(
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
                    viewModelStateFlow.value = viewModelStateFlow.value.copy(
                        isExtracting = false,
                        isExtractComplete = true,
                        statusMessage = event.message,
                        statusMessageLinkText = null,
                    )
                }

                is ExtractJobCompletionWatcher.CompletionUiEvent.Failed -> {
                    viewModelStateFlow.value = viewModelStateFlow.value.copy(
                        isExtracting = false,
                        isExtractComplete = false,
                        statusMessage = event.message,
                        statusMessageLinkText = ExtractOutputLocationResolver
                            .parseDuplicateOutputName(event.message),
                    )
                }

                is ExtractJobCompletionWatcher.CompletionUiEvent.Cancelled -> {
                    viewModelStateFlow.value = viewModelStateFlow.value.copy(
                        isExtracting = false,
                        isExtractComplete = false,
                        statusMessage = event.message,
                        statusMessageLinkText = null,
                    )
                }
            }
        }
    }

    private suspend fun deleteSourceIfRequested(): Boolean {
        if (!deleteSourceRequested) {
            return true
        }
        deleteSourceRequested = false
        val deleted = withContext(Dispatchers.IO) {
            deleteOriginalSource()
        }
        if (!deleted) {
            viewModelStateFlow.value = viewModelStateFlow.value.copy(
                statusMessage = "元のファイルを削除できませんでした",
                statusMessageLinkText = null,
            )
        }
        return deleted
    }

    private fun deleteOriginalSource(): Boolean {
        val application = getApplication<Application>()
        val sourceUri = Uri.parse(args.sourceUri)
        val deletedByUri = when (sourceUri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                sourceUri.path?.let { path ->
                    runCatching { File(path).delete() }.getOrDefault(false)
                } ?: false
            }

            ContentResolver.SCHEME_CONTENT -> {
                runCatching {
                    DocumentFile.fromSingleUri(application, sourceUri)?.delete() == true
                }.getOrDefault(false) ||
                    runCatching {
                        application.contentResolver.delete(sourceUri, null, null) > 0
                    }.getOrDefault(false)
            }

            else -> false
        }
        if (deletedByUri) {
            return true
        }
        if (ExternalExtractStagingSupport.isStagedSource(args.sourcePath, application.cacheDir)) {
            return false
        }
        return runCatching {
            val sourceFile = File(args.sourcePath)
            sourceFile.isFile && sourceFile.delete()
        }.getOrDefault(false)
    }

    private fun deleteStagedSourceIfNeeded() {
        ExternalExtractStagingSupport.deleteStagedSourceIfNeeded(
            args.sourcePath,
            getApplication<Application>().cacheDir,
        )
    }

    private suspend fun openExtractOutput(jobId: Long): Boolean {
        val meta = extractJobRepository.getJobMeta(jobId) ?: return false
        return when (
            val result = ExtractOutputLocationResolver.resolveOpenExtractResult(
                meta = meta,
                storageRepository = storageRepository,
                errorMessage = viewModelStateFlow.value.statusMessage,
            )
        ) {
            is ExtractOutputLocationResolver.OpenExtractResult.OpenFile -> {
                viewModelEventChannel.send(
                    ViewModelEvent.OpenOutputFile(
                        viewSourceUri = result.target.viewSourceUri,
                        fileName = result.target.fileName,
                        mimeType = result.target.mimeType,
                    ),
                )
                true
            }

            is ExtractOutputLocationResolver.OpenExtractResult.NavigateToOutput -> {
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToFileBrowser(
                        fileId = result.target.fileId,
                        displayPath = result.target.displayPath,
                    ),
                )
                true
            }

            is ExtractOutputLocationResolver.OpenExtractResult.OpenFolder -> {
                viewModelEventChannel.send(
                    ViewModelEvent.OpenOutputFolder(result.target.absolutePath),
                )
                true
            }

            null -> {
                viewModelStateFlow.value = viewModelStateFlow.value.copy(
                    statusMessage = "解凍結果を開けませんでした",
                    statusMessageLinkText = null,
                )
                false
            }
        }
    }

    private data class ViewModelState(
        val defaultName: String,
        val isExtracting: Boolean,
        val isExtractComplete: Boolean,
        val statusMessage: String?,
        val statusMessageLinkText: String?,
        val isResultActionInProgress: Boolean,
        val progress: Float?,
        val progressText: String?,
    )

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
