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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.ui.upload.OperationFileFilter
import net.matsudamper.folderviewer.ui.upload.OperationFileRow
import net.matsudamper.folderviewer.ui.upload.OperationFileStatus
import net.matsudamper.folderviewer.ui.upload.PasteDetailUiState
import net.matsudamper.folderviewer.viewmodel.util.FileUtil
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

    private val filterState = MutableStateFlow(FileFilterState())

    private var initJob: Job? = null

    fun init(jobId: Long) {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            val meta = pasteJobRepository.getJobMeta(jobId) ?: return@launch
            combine(
                operationRepository.observeProgressById(jobId),
                pasteJobRepository.observeDuplicateFiles(jobId),
                pasteJobRepository.observeFiles(jobId),
                filterState,
            ) { progress, duplicates, allFiles, filter ->
                if (progress == null) return@combine null
                createUiState(
                    jobId = jobId,
                    meta = meta,
                    progress = progress,
                    duplicates = duplicates,
                    allFiles = allFiles,
                    filter = filter,
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
        allFiles: List<PasteJobRepository.PasteFile>,
        filter: FileFilterState,
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

        val fileRows = allFiles
            .filterNot { it.isDirectory }
            .map { file ->
                OperationFileRow(
                    key = file.id.toString(),
                    fileName = file.fileName,
                    sourcePath = sourcePath(meta, file),
                    destinationPath = destinationPath(meta, file),
                    status = file.status.toFileStatus(),
                    errorMessage = file.errorMessage,
                )
            }

        val isRunning = progress.status == OperationRepository.OperationStatus.RUNNING
        val isActive = isRunning || progress.status == OperationRepository.OperationStatus.ENQUEUED

        val canApply = duplicateItems.isNotEmpty() &&
            duplicateItems.all { it.resolution != null } &&
            !isActive
        val overallProgress = if (isActive && progress.totalBytes > 0) {
            progress.completedBytes.toFloat() / progress.totalBytes.toFloat()
        } else {
            null
        }
        val fileCountText = if (isActive) {
            "${progress.completedFiles}/${progress.totalFiles}"
        } else {
            null
        }
        val sizeProgressText = if (isActive && progress.totalBytes > 0) {
            "${formatFileSize(progress.completedBytes)} / ${formatFileSize(progress.totalBytes)}"
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

            override fun onRetryClick() {
                retry(jobId)
            }
        }

        val canRetry = uiStatus == PasteDetailUiState.Status.FAILED && progress.failedFiles > 0

        return PasteDetailUiState(
            jobName = "${progress.totalFiles}ファイルを$modeText",
            statusText = statusText,
            status = uiStatus,
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            duplicateFiles = duplicateItems,
            files = fileRows,
            fileFilter = filter.toUiFilter(),
            canRetry = canRetry,
            canApply = canApply,
            progress = overallProgress,
            fileCountText = fileCountText,
            sizeProgressText = sizeProgressText,
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
        val isImage = !file.isDirectory && FileUtil.isImage(file.fileName)
        return PasteDetailUiState.DuplicateFileItem(
            fileId = file.id,
            fileName = file.fileName,
            sourcePath = "${file.sourceFileId.storageId}/${file.sourceFileId.id}",
            sourceSize = file.fileSize,
            sourceSizeText = formatFileSize(file.fileSize),
            destinationPath = destinationPath(meta, file),
            destinationSize = file.destinationFileSize,
            destinationSizeText = formatFileSize(file.destinationFileSize),
            sourceThumbnail = if (isImage) {
                FileImageSource.Thumbnail(fileId = file.sourceFileId)
            } else {
                null
            },
            destinationThumbnail = if (isImage) {
                file.destinationFileId?.let { FileImageSource.Thumbnail(fileId = it) }
            } else {
                null
            },
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

    private fun sourcePath(
        meta: PasteJobRepository.PasteJobMeta,
        file: PasteJobRepository.PasteFile,
    ): String {
        return if (file.relativePath.isEmpty()) {
            "${meta.sourceDisplayPath}/${file.fileName}"
        } else {
            "${meta.sourceDisplayPath}/${file.relativePath}/${file.fileName}"
        }
    }

    private fun retry(jobId: Long) {
        viewModelScope.launch {
            pasteJobRepository.retryJob(jobId)

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

    private fun OperationRepository.FileStatus.toFileStatus(): OperationFileStatus {
        return when (this) {
            OperationRepository.FileStatus.COMPLETED -> OperationFileStatus.COMPLETED
            OperationRepository.FileStatus.FAILED -> OperationFileStatus.FAILED
            OperationRepository.FileStatus.PENDING,
            OperationRepository.FileStatus.RUNNING,
            -> OperationFileStatus.PENDING
        }
    }

    private fun FileFilterState.toUiFilter(): OperationFileFilter {
        return OperationFileFilter(
            showCompleted = showCompleted,
            showPending = showPending,
            showFailed = showFailed,
            onToggleCompleted = { filterState.update { it.copy(showCompleted = !it.showCompleted) } },
            onTogglePending = { filterState.update { it.copy(showPending = !it.showPending) } },
            onToggleFailed = { filterState.update { it.copy(showFailed = !it.showFailed) } },
        )
    }

    private data class FileFilterState(
        val showCompleted: Boolean = true,
        val showPending: Boolean = true,
        val showFailed: Boolean = true,
    )

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
