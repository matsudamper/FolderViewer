package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadProgressUiState
import net.matsudamper.folderviewer.viewmodel.worker.FileUploadWorker

@HiltViewModel
class UploadProgressViewModel @Inject constructor(
    application: Application,
    private val uploadJobRepository: UploadJobRepository,
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
            viewModelScope.launch {
                val uuid = runCatching { UUID.fromString(item.id) }.getOrNull() ?: return@launch
                val job = uploadJobRepository.getJob(uuid.toString()) ?: return@launch

                if (item.state == UploadProgressUiState.UploadState.FAILED) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToUploadErrorDetail(
                            workerId = job.workerId,
                        ),
                    )
                } else {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToFileBrowser(
                            storageId = job.storageId,
                            fileObjectId = job.fileObjectId,
                            displayPath = job.displayPath,
                        ),
                    )
                }
            }
        }

        override fun onClearHistoryClick() {
            showClearConfirmDialog.value = true
        }

        override fun onClearHistoryConfirm() {
            showClearConfirmDialog.value = false
            viewModelScope.launch {
                uploadJobRepository.deleteAllJobs()
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
                    UploadProgressUiState(
                        uploadItems = state.jobs.map { job ->
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

                            val currentBytes = workInfo?.progress?.getLong("CurrentBytes", 0L) ?: 0L
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
                        },
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
                workManager.getWorkInfosByTagFlow(FileUploadWorker.TAG_UPLOAD),
            ) { jobs, workInfoList ->
                val workInfoMap = workInfoList.associateBy { it.id.toString() }
                ViewModelState(
                    jobs = jobs.sortedByDescending {
                        workInfoMap[it.workerId]?.state == WorkInfo.State.RUNNING
                    },
                    workInfoMap = workInfoMap,
                )
            }.collect { state ->
                viewModelStateFlow.value = state
            }
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToFileBrowser(
            val storageId: StorageId,
            val fileObjectId: FileObjectId,
            val displayPath: String,
        ) : ViewModelEvent
        data class NavigateToUploadErrorDetail(
            val workerId: String,
        ) : ViewModelEvent
    }

    private data class ViewModelState(
        val jobs: List<UploadJobRepository.UploadJob> = emptyList(),
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
