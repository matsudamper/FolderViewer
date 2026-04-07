package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.ui.upload.PasteDetailUiState
import net.matsudamper.folderviewer.viewmodel.worker.FilePasteWorker

@HiltViewModel
class PasteDetailViewModel @Inject constructor(
    application: Application,
    private val pasteJobRepository: PasteJobRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<PasteDetailUiState?>(null)
    val uiState: StateFlow<PasteDetailUiState?> = _uiState.asStateFlow()

    fun init(jobId: Long) {
        viewModelScope.launch {
            combine(
                pasteJobRepository.observeJob(jobId),
                pasteJobRepository.observeDuplicateFiles(jobId),
                pasteJobRepository.observeCompletedFiles(jobId),
                pasteJobRepository.observeFailedFiles(jobId),
            ) { job, duplicates, completed, failedFiles ->
                if (job == null) return@combine null

                val modeText = when (job.mode) {
                    net.matsudamper.folderviewer.repository.ClipboardRepository.ClipboardMode.Copy -> "コピー"
                    net.matsudamper.folderviewer.repository.ClipboardRepository.ClipboardMode.Cut -> "カット"
                }

                val statusText = when (job.status) {
                    PasteJobRepository.PasteJobStatus.RUNNING -> "実行中"
                    PasteJobRepository.PasteJobStatus.PAUSED -> "一時停止"
                    PasteJobRepository.PasteJobStatus.COMPLETED -> "完了"
                    PasteJobRepository.PasteJobStatus.FAILED -> "失敗"
                    PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION -> "解決待ち"
                }

                val uiStatus = when (job.status) {
                    PasteJobRepository.PasteJobStatus.RUNNING -> PasteDetailUiState.Status.RUNNING
                    PasteJobRepository.PasteJobStatus.PAUSED -> PasteDetailUiState.Status.PAUSED
                    PasteJobRepository.PasteJobStatus.COMPLETED -> PasteDetailUiState.Status.COMPLETED
                    PasteJobRepository.PasteJobStatus.FAILED -> PasteDetailUiState.Status.FAILED
                    PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION -> PasteDetailUiState.Status.WAITING_RESOLUTION
                }

                val duplicateItems = duplicates.map { file ->
                    val sourcePath = "${file.sourceFileId.storageId}/${file.sourceFileId.id}"
                    val destinationPath = if (file.destinationRelativePath.isEmpty()) {
                        "${job.destinationDisplayPath}/${file.fileName}"
                    } else {
                        "${job.destinationDisplayPath}/${file.destinationRelativePath}/${file.fileName}"
                    }

                    val uiResolution = when (file.resolution) {
                        PasteJobRepository.DuplicateResolution.KEEP_DESTINATION -> PasteDetailUiState.Resolution.KEEP_DESTINATION
                        PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE -> PasteDetailUiState.Resolution.OVERWRITE_WITH_SOURCE
                        PasteJobRepository.DuplicateResolution.PENDING, null -> null
                    }

                    PasteDetailUiState.DuplicateFileItem(
                        fileId = file.id,
                        fileName = file.fileName,
                        sourcePath = sourcePath,
                        sourceSize = file.fileSize,
                        sourceSizeText = formatFileSize(file.fileSize),
                        destinationPath = destinationPath,
                        destinationSize = file.destinationFileSize,
                        destinationSizeText = formatFileSize(file.destinationFileSize),
                        resolution = uiResolution,
                        onKeepDestination = {
                            resolveFile(file.id, PasteJobRepository.DuplicateResolution.KEEP_DESTINATION, jobId)
                        },
                        onOverwriteWithSource = {
                            resolveFile(file.id, PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE, jobId)
                        },
                    )
                }

                val completedItems = completed.map { file ->
                    val path = if (file.destinationRelativePath.isEmpty()) {
                        "${job.destinationDisplayPath}/${file.fileName}"
                    } else {
                        "${job.destinationDisplayPath}/${file.destinationRelativePath}/${file.fileName}"
                    }
                    val uiResolution = when (file.resolution) {
                        PasteJobRepository.DuplicateResolution.KEEP_DESTINATION -> PasteDetailUiState.Resolution.KEEP_DESTINATION
                        PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE -> PasteDetailUiState.Resolution.OVERWRITE_WITH_SOURCE
                        else -> PasteDetailUiState.Resolution.NONE
                    }
                    PasteDetailUiState.CompletedFileItem(
                        fileName = file.fileName,
                        path = path,
                        sizeText = formatFileSize(file.fileSize),
                        resolution = uiResolution,
                    )
                }

                val canApply = duplicateItems.isNotEmpty() &&
                    duplicateItems.all { it.resolution != null } &&
                    job.status == PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION

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

                val failedItems = failedFiles.map { file ->
                    val path = if (file.destinationRelativePath.isEmpty()) {
                        "${job.destinationDisplayPath}/${file.fileName}"
                    } else {
                        "${job.destinationDisplayPath}/${file.destinationRelativePath}/${file.fileName}"
                    }
                    PasteDetailUiState.FailedFileItem(
                        fileName = file.fileName,
                        path = path,
                        errorMessage = file.errorMessage ?: "",
                    )
                }

                PasteDetailUiState(
                    jobName = "${job.totalFiles}ファイルを${modeText}",
                    statusText = statusText,
                    status = uiStatus,
                    errorMessage = job.errorMessage,
                    errorCause = job.errorCause,
                    duplicateFiles = duplicateItems,
                    completedFiles = completedItems,
                    failedFiles = failedItems,
                    canApply = canApply,
                    callbacks = callbacks,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun resolveFile(fileId: Long, resolution: PasteJobRepository.DuplicateResolution, jobId: Long) {
        viewModelScope.launch {
            pasteJobRepository.resolveFile(fileId, resolution)
            val unresolvedCount = pasteJobRepository.countUnresolvedDuplicates(jobId)
            val job = pasteJobRepository.getJobById(jobId) ?: return@launch
            pasteJobRepository.updateResolvedCount(jobId, job.duplicateFiles - unresolvedCount)
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
                status = PasteJobRepository.PasteJobStatus.RUNNING,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
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
