package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadProgressUiState
import net.matsudamper.folderviewer.viewmodel.worker.FilePasteWorker
import net.matsudamper.folderviewer.viewmodel.worker.FileUploadWorker
import net.matsudamper.folderviewer.viewmodel.worker.FolderUploadWorker

@HiltViewModel
class UploadProgressViewModel @Inject constructor(
    application: Application,
    private val uploadJobRepository: UploadJobRepository,
    private val pasteJobRepository: PasteJobRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow = MutableStateFlow(ViewModelState())

    private val showClearConfirmDialog = MutableStateFlow(false)

    private val callbacks = object : UploadProgressUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }

        override fun onItemClick(item: UploadProgressUiState.UploadItem) {
            if (item is UploadProgressUiState.UploadItem.Paste) {
                viewModelScope.launch {
                    val jobId = item.id.toLongOrNull() ?: return@launch
                    viewModelEventChannel.send(ViewModelEvent.NavigateToPasteDetail(jobId))
                }
                return
            }
            viewModelScope.launch {
                val uuid = runCatching { UUID.fromString(item.id) }.getOrNull() ?: return@launch
                val job = uploadJobRepository.getJob(uuid.toString()) ?: return@launch

                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToUploadDetail(
                        workerId = job.workerId,
                    ),
                )
            }
        }

        override fun onClearHistoryClick() {
            showClearConfirmDialog.value = true
        }

        override fun onClearHistoryConfirm() {
            showClearConfirmDialog.value = false
            viewModelScope.launch {
                val state = viewModelStateFlow.value
                state.jobs.forEach { job ->
                    val workInfo = state.workInfoMap[job.workerId]
                    val isActive = workInfo?.state == WorkInfo.State.RUNNING ||
                        workInfo?.state == WorkInfo.State.ENQUEUED ||
                        workInfo?.state == WorkInfo.State.BLOCKED
                    if (!isActive) {
                        uploadJobRepository.deleteJob(job.workerId)
                    }
                }
                state.pasteJobs.forEach { job ->
                    val isActive = job.status == PasteJobRepository.PasteJobStatus.RUNNING ||
                        job.status == PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION
                    if (!isActive) {
                        pasteJobRepository.deleteJob(job.id)
                    }
                }
            }
        }

        override fun onClearHistoryDismiss() {
            showClearConfirmDialog.value = false
        }
    }

    val uiState: StateFlow<UploadProgressUiState> = MutableStateFlow(
        UploadProgressUiState(
            uploadItems = emptyList(),
            showClearConfirmDialog = false,
            callbacks = callbacks,
        ),
    ).also { mutableState ->
        viewModelScope.launch {
            combine(
                viewModelStateFlow,
                showClearConfirmDialog,
            ) { state, showDialog ->
                state to showDialog
            }.collectLatest { (state, showDialog) ->
                mutableState.update {
                    val uploadItems = state.jobs.map { job ->
                        val workInfo = state.workInfoMap[job.workerId]

                        val uploadState = when (workInfo?.state) {
                            WorkInfo.State.ENQUEUED -> UploadProgressUiState.UploadState.ENQUEUED
                            WorkInfo.State.RUNNING -> UploadProgressUiState.UploadState.RUNNING
                            WorkInfo.State.SUCCEEDED -> UploadProgressUiState.UploadState.SUCCEEDED
                            WorkInfo.State.FAILED -> UploadProgressUiState.UploadState.FAILED
                            WorkInfo.State.BLOCKED -> UploadProgressUiState.UploadState.ENQUEUED
                            WorkInfo.State.CANCELLED -> UploadProgressUiState.UploadState.CANCELLED
                            null -> UploadProgressUiState.UploadState.SUCCEEDED
                        }

                        val currentBytes = workInfo?.progress?.getLong(FolderUploadWorker.KEY_CURRENT_BYTES, 0L) ?: 0L
                        val totalBytes = workInfo?.progress?.getLong("TotalBytes", 0L) ?: 0L
                        val progress = if (totalBytes > 0) {
                            currentBytes.toFloat() / totalBytes.toFloat()
                        } else {
                            null
                        }

                        val progressText = if (uploadState == UploadProgressUiState.UploadState.RUNNING) {
                            "${formatFileSize(currentBytes)}/${formatFileSize(totalBytes)}"
                        } else {
                            null
                        }

                        if (job.isFolder) {
                            UploadProgressUiState.UploadItem.Folder(
                                id = job.workerId,
                                name = job.name,
                                state = uploadState,
                                canNavigate = true,
                                fileCount = 0,
                                progress = progress,
                                progressText = progressText,
                            )
                        } else {
                            UploadProgressUiState.UploadItem.File(
                                id = job.workerId,
                                name = job.name,
                                state = uploadState,
                                canNavigate = true,
                                progress = progress,
                                progressText = progressText,
                            )
                        }
                    }

                    val pasteItems = state.pasteJobs.map { job ->
                        val pasteState = when (job.status) {
                            PasteJobRepository.PasteJobStatus.RUNNING -> UploadProgressUiState.UploadState.RUNNING
                            PasteJobRepository.PasteJobStatus.PAUSED -> UploadProgressUiState.UploadState.PAUSED
                            PasteJobRepository.PasteJobStatus.COMPLETED -> UploadProgressUiState.UploadState.SUCCEEDED
                            PasteJobRepository.PasteJobStatus.FAILED -> UploadProgressUiState.UploadState.FAILED
                            PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION -> UploadProgressUiState.UploadState.WAITING_RESOLUTION
                        }

                        val overallProgress = if (job.totalBytes > 0) {
                            (job.completedBytes + job.currentFileBytes).toFloat() / job.totalBytes.toFloat()
                        } else {
                            null
                        }

                        val currentFileProgress = if (job.currentFileTotalBytes > 0) {
                            job.currentFileBytes.toFloat() / job.currentFileTotalBytes.toFloat()
                        } else {
                            null
                        }

                        val progressText = if (pasteState == UploadProgressUiState.UploadState.RUNNING) {
                            val completed = formatFileSize(job.completedBytes + job.currentFileBytes)
                            val total = formatFileSize(job.totalBytes)
                            val duplicateText = if (job.duplicateFiles > 0) " (重複${job.duplicateFiles}件)" else ""
                            "${job.completedFiles}/${job.totalFiles}ファイル ($completed/$total)$duplicateText"
                        } else if (pasteState == UploadProgressUiState.UploadState.WAITING_RESOLUTION && job.duplicateFiles > 0) {
                            "重複${job.duplicateFiles}件"
                        } else {
                            null
                        }

                        val modeText = when (job.mode) {
                            ClipboardRepository.ClipboardMode.Copy -> "コピー"
                            ClipboardRepository.ClipboardMode.Cut -> "カット"
                        }

                        val pasteCallbacks = object : UploadProgressUiState.PasteCallbacks {
                            override fun onPauseClick() {
                                pausePasteJob(job)
                            }

                            override fun onResumeClick() {
                                resumePasteJob(job)
                            }
                        }

                        UploadProgressUiState.UploadItem.Paste(
                            id = job.id.toString(),
                            name = "${job.totalFiles}ファイルを${modeText}",
                            state = pasteState,
                            canNavigate = true,
                            mode = modeText,
                            totalFiles = job.totalFiles,
                            completedFiles = job.completedFiles,
                            duplicateFiles = job.duplicateFiles,
                            currentFileName = job.currentFileName,
                            currentFileProgress = currentFileProgress,
                            progress = overallProgress,
                            progressText = progressText,
                            isPausable = pasteState == UploadProgressUiState.UploadState.RUNNING,
                            isResumable = pasteState == UploadProgressUiState.UploadState.PAUSED,
                            pasteCallbacks = pasteCallbacks,
                        )
                    }

                    UploadProgressUiState(
                        uploadItems = uploadItems + pasteItems,
                        showClearConfirmDialog = showDialog,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(getApplication())
            combine(
                uploadJobRepository.getAllJobs(),
                pasteJobRepository.getAllJobs(),
                workManager.getWorkInfosByTagFlow(FileUploadWorker.TAG_UPLOAD),
            ) { jobs, pasteJobs, workInfoList ->
                val workInfoMap = workInfoList.associateBy { it.id.toString() }
                ViewModelState(
                    jobs = jobs.sortedByDescending {
                        workInfoMap[it.workerId]?.state == WorkInfo.State.RUNNING
                    },
                    pasteJobs = pasteJobs,
                    workInfoMap = workInfoMap,
                )
            }.collect { state ->
                viewModelStateFlow.value = state
            }
        }
    }

    private fun pausePasteJob(job: PasteJobRepository.PasteJob) {
        viewModelScope.launch {
            val workerId = job.workerId ?: return@launch
            val uuid = runCatching { UUID.fromString(workerId) }.getOrNull() ?: return@launch
            WorkManager.getInstance(getApplication()).cancelWorkById(uuid)
        }
    }

    private fun resumePasteJob(job: PasteJobRepository.PasteJob) {
        viewModelScope.launch {
            val inputData = Data.Builder()
                .putLong(FilePasteWorker.KEY_PASTE_JOB_ID, job.id)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FilePasteWorker>()
                .setInputData(inputData)
                .addTag(FilePasteWorker.TAG_PASTE)
                .build()

            pasteJobRepository.updateStatus(
                jobId = job.id,
                status = PasteJobRepository.PasteJobStatus.RUNNING,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToUploadDetail(
            val workerId: String,
        ) : ViewModelEvent
        data class NavigateToPasteDetail(
            val jobId: Long,
        ) : ViewModelEvent
    }

    private data class ViewModelState(
        val jobs: List<UploadJobRepository.UploadJob> = emptyList(),
        val pasteJobs: List<PasteJobRepository.PasteJob> = emptyList(),
        val workInfoMap: Map<String, WorkInfo> = emptyMap(),
    )

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
}
