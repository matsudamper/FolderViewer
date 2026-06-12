package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.ui.upload.PasteDetailUiState
import net.matsudamper.folderviewer.viewmodel.worker.FilePasteWorker

@HiltViewModel
class PasteDetailViewModel @Inject constructor(
    application: Application,
    private val operationRepository: OperationRepository,
    private val pasteJobRepository: PasteJobRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<PasteDetailUiState?>(null)
    val uiState: StateFlow<PasteDetailUiState?> = _uiState.asStateFlow()

    private var initJob: Job? = null

    fun init(jobId: Long) {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            val meta = pasteJobRepository.getJobMeta(jobId) ?: return@launch
            combine(
                operationRepository.observeProgressById(jobId),
                pasteJobRepository.observeDuplicateFiles(jobId),
                pasteJobRepository.observeCompletedFiles(jobId),
                pasteJobRepository.observeFailedFiles(jobId),
            ) { progress, duplicates, completed, failedFiles ->
                if (progress == null) return@combine null
                createUiState(
                    jobId = jobId,
                    meta = meta,
                    progress = progress,
                    duplicates = duplicates,
                    completed = completed,
                    failedFiles = failedFiles,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun createUiState(
        jobId: Long,
        meta: PasteJobRepository.PasteJobMeta,
        progress: OperationRepository.OperationProgress,
        duplicates: List<PasteJobRepository.PasteFile>,
        completed: List<PasteJobRepository.PasteFile>,
        failedFiles: List<PasteJobRepository.PasteFile>,
    ): PasteDetailUiState {
        val modeText = when (meta.mode) {
            ClipboardRepository.ClipboardMode.Copy -> "コピー"
            ClipboardRepository.ClipboardMode.Cut -> "カット"
        }

        val statusText = when (progress.status) {
            OperationRepository.OperationStatus.ENQUEUED -> "待機中"
            OperationRepository.OperationStatus.RUNNING -> "実行中"
            OperationRepository.OperationStatus.PAUSED -> "一時停止"
            OperationRepository.OperationStatus.COMPLETED -> "完了"
            OperationRepository.OperationStatus.FAILED -> "失敗"
            OperationRepository.OperationStatus.CANCELLED -> "キャンセル"
            OperationRepository.OperationStatus.WAITING_RESOLUTION -> "解決待ち"
        }

        val uiStatus = when (progress.status) {
            OperationRepository.OperationStatus.ENQUEUED -> PasteDetailUiState.Status.ENQUEUED
            OperationRepository.OperationStatus.RUNNING -> PasteDetailUiState.Status.RUNNING
            OperationRepository.OperationStatus.PAUSED -> PasteDetailUiState.Status.PAUSED
            OperationRepository.OperationStatus.COMPLETED -> PasteDetailUiState.Status.COMPLETED
            OperationRepository.OperationStatus.FAILED,
            OperationRepository.OperationStatus.CANCELLED,
            -> PasteDetailUiState.Status.FAILED
            OperationRepository.OperationStatus.WAITING_RESOLUTION ->
                PasteDetailUiState.Status.WAITING_RESOLUTION
        }

        val duplicateItems = duplicates.map { file ->
            createDuplicateItem(meta = meta, file = file)
        }

        val completedItems = completed.map { file ->
            val uiResolution = when (file.resolution) {
                PasteJobRepository.DuplicateResolution.KEEP_DESTINATION ->
                    PasteDetailUiState.Resolution.KEEP_DESTINATION
                PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE ->
                    PasteDetailUiState.Resolution.OVERWRITE_WITH_SOURCE
                else -> PasteDetailUiState.Resolution.NONE
            }
            PasteDetailUiState.CompletedFileItem(
                fileName = file.fileName,
                path = destinationPath(meta, file),
                sizeText = formatFileSize(file.fileSize),
                resolution = uiResolution,
            )
        }

        val failedItems = failedFiles.map { file ->
            PasteDetailUiState.FailedFileItem(
                fileName = file.fileName,
                path = destinationPath(meta, file),
                errorMessage = file.errorMessage ?: "",
            )
        }

        val canApply = duplicateItems.isNotEmpty() &&
            duplicateItems.all { it.resolution != null } &&
            progress.status == OperationRepository.OperationStatus.WAITING_RESOLUTION

        val isRunning = progress.status == OperationRepository.OperationStatus.RUNNING
        val isActive = isRunning || progress.status == OperationRepository.OperationStatus.ENQUEUED
        val overallProgress = if (isActive && progress.totalBytes > 0) {
            progress.completedBytes.toFloat() / progress.totalBytes.toFloat()
        } else {
            null
        }
        val currentFileTotalBytes = progress.currentFileTotalBytes
        val currentFileProgress = if (isRunning && currentFileTotalBytes != null && currentFileTotalBytes > 0) {
            (progress.currentFileBytes ?: 0L).toFloat() / currentFileTotalBytes.toFloat()
        } else {
            null
        }

        val callbacks = object : PasteDetailUiState.Callbacks {
            override fun onBackClick() {
                viewModelScope.launch {
                    viewModelEventChannel.send(ViewModelEvent.NavigateBack)
                }
            }

            override fun onApplyResolutions() {
                applyResolutions(jobId)
            }
        }

        return PasteDetailUiState(
            jobName = "${progress.totalFiles}ファイルを$modeText",
            statusText = statusText,
            status = uiStatus,
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            duplicateFiles = duplicateItems,
            completedFiles = completedItems,
            failedFiles = failedItems,
            canApply = canApply,
            progress = overallProgress,
            currentFileName = if (isRunning) progress.currentFileName else null,
            currentFileProgress = currentFileProgress,
            callbacks = callbacks,
        )
    }

    private fun createDuplicateItem(
        meta: PasteJobRepository.PasteJobMeta,
        file: PasteJobRepository.PasteFile,
    ): PasteDetailUiState.DuplicateFileItem {
        val uiResolution = when (file.resolution) {
            PasteJobRepository.DuplicateResolution.KEEP_DESTINATION ->
                PasteDetailUiState.Resolution.KEEP_DESTINATION
            PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE ->
                PasteDetailUiState.Resolution.OVERWRITE_WITH_SOURCE
            PasteJobRepository.DuplicateResolution.PENDING, null -> null
        }
        return PasteDetailUiState.DuplicateFileItem(
            fileId = file.id,
            fileName = file.fileName,
            sourcePath = "${file.sourceFileId.storageId}/${file.sourceFileId.id}",
            sourceSize = file.fileSize,
            sourceSizeText = formatFileSize(file.fileSize),
            destinationPath = destinationPath(meta, file),
            destinationSize = file.destinationFileSize,
            destinationSizeText = formatFileSize(file.destinationFileSize),
            resolution = uiResolution,
            onKeepDestination = {
                resolveFile(file.id, PasteJobRepository.DuplicateResolution.KEEP_DESTINATION)
            },
            onOverwriteWithSource = {
                resolveFile(file.id, PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE)
            },
        )
    }

    private fun destinationPath(
        meta: PasteJobRepository.PasteJobMeta,
        file: PasteJobRepository.PasteFile,
    ): String {
        return if (file.relativePath.isEmpty()) {
            "${meta.destinationDisplayPath}/${file.fileName}"
        } else {
            "${meta.destinationDisplayPath}/${file.relativePath}/${file.fileName}"
        }
    }

    private fun resolveFile(fileId: Long, resolution: PasteJobRepository.DuplicateResolution) {
        viewModelScope.launch {
            pasteJobRepository.resolveFile(fileId, resolution)
        }
    }

    private fun applyResolutions(jobId: Long) {
        viewModelScope.launch {
            val unresolvedCount = pasteJobRepository.countUnresolvedDuplicates(jobId)
            if (unresolvedCount > 0) return@launch

            val inputData = Data.Builder()
                .putLong(FilePasteWorker.KEY_PASTE_JOB_ID, jobId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FilePasteWorker>()
                .setInputData(inputData)
                .addTag(FilePasteWorker.TAG_PASTE)
                .build()

            pasteJobRepository.updateStatus(
                jobId = jobId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "paste_job_$jobId",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> String.format(Locale.ROOT, "%.1fGB", bytes / gb)
            bytes >= mb -> String.format(Locale.ROOT, "%.1fMB", bytes / mb)
            else -> String.format(Locale.ROOT, "%.1fKB", bytes / kb)
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
