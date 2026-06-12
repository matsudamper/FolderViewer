package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import java.util.UUID
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
import net.matsudamper.folderviewer.ui.upload.UploadProgressUiState
import net.matsudamper.folderviewer.viewmodel.worker.FilePasteWorker

@HiltViewModel
class UploadProgressViewModel @Inject constructor(
    application: Application,
    private val operationRepository: OperationRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val showClearConfirmDialog = MutableStateFlow(false)

    private val callbacks = object : UploadProgressUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }

        override fun onItemClick(item: UploadProgressUiState.UploadItem) {
            viewModelScope.launch {
                when (item) {
                    is UploadProgressUiState.UploadItem.Paste -> {
                        val jobId = item.id.toLongOrNull() ?: return@launch
                        viewModelEventChannel.send(ViewModelEvent.NavigateToPasteDetail(jobId))
                    }
                    is UploadProgressUiState.UploadItem.Delete -> {
                        val opId = item.id.toLongOrNull() ?: return@launch
                        viewModelEventChannel.send(ViewModelEvent.NavigateToDeleteDetail(opId))
                    }
                    else -> {
                        val uuid = runCatching { UUID.fromString(item.id) }.getOrNull()
                            ?: return@launch
                        viewModelEventChannel.send(
                            ViewModelEvent.NavigateToUploadDetail(workerId = uuid.toString()),
                        )
                    }
                }
            }
        }

        override fun onClearHistoryClick() {
            showClearConfirmDialog.value = true
        }

        override fun onClearHistoryConfirm() {
            showClearConfirmDialog.value = false
            viewModelScope.launch {
                operationRepository.deleteNonActiveHistory()
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
                operationRepository.observeProgress(),
                showClearConfirmDialog,
            ) { operations, showDialog ->
                UploadProgressUiState(
                    uploadItems = operations.map { createItem(it) },
                    showClearConfirmDialog = showDialog,
                    callbacks = callbacks,
                )
            }.collect { state ->
                mutableState.value = state
            }
        }
    }.asStateFlow()

    private fun createItem(op: OperationRepository.OperationProgress): UploadProgressUiState.UploadItem {
        return when (op.type) {
            OperationRepository.OperationType.PASTE -> createPasteItem(op)
            OperationRepository.OperationType.DELETE -> createDeleteItem(op)
            OperationRepository.OperationType.UPLOAD_FOLDER -> createUploadItem(op, isFolder = true)
            OperationRepository.OperationType.UPLOAD_FILE, null -> createUploadItem(op, isFolder = false)
        }
    }

    private fun createUploadItem(
        op: OperationRepository.OperationProgress,
        isFolder: Boolean,
    ): UploadProgressUiState.UploadItem {
        val state = mapState(op.status)
        val isRunning = state == UploadProgressUiState.UploadState.RUNNING
        val progress = if (isRunning && op.totalBytes > 0) {
            op.completedBytes.toFloat() / op.totalBytes.toFloat()
        } else {
            null
        }
        val progressText = if (isRunning && op.totalBytes > 0) {
            "${formatFileSize(op.completedBytes)}/${formatFileSize(op.totalBytes)}"
        } else {
            null
        }
        val id = op.workerId ?: op.id.toString()
        val name = "アップロード ${op.totalFiles}件"
        val description = op.currentFileName ?: op.description
        return if (isFolder) {
            UploadProgressUiState.UploadItem.Folder(
                id = id,
                name = name,
                description = description,
                state = state,
                canNavigate = op.workerId != null,
                progress = progress,
                progressText = progressText,
            )
        } else {
            UploadProgressUiState.UploadItem.File(
                id = id,
                name = name,
                description = description,
                state = state,
                canNavigate = op.workerId != null,
                progress = progress,
                progressText = progressText,
            )
        }
    }

    private fun createPasteItem(op: OperationRepository.OperationProgress): UploadProgressUiState.UploadItem {
        val state = mapState(op.status)
        val isActive = state == UploadProgressUiState.UploadState.RUNNING ||
            state == UploadProgressUiState.UploadState.PAUSED

        val overallProgress = if (isActive && op.totalBytes > 0) {
            op.completedBytes.toFloat() / op.totalBytes.toFloat()
        } else {
            null
        }
        val currentFileTotalBytes = op.currentFileTotalBytes
        val currentFileProgress = if (isActive) {
            if (currentFileTotalBytes != null && currentFileTotalBytes > 0) {
                (op.currentFileBytes ?: 0L).toFloat() / currentFileTotalBytes.toFloat()
            } else {
                0f
            }
        } else {
            null
        }

        val modeText = when (op.pasteMode) {
            ClipboardRepository.ClipboardMode.Cut -> "カット"
            ClipboardRepository.ClipboardMode.Copy, null -> "コピー"
        }
        val progressText = createPasteProgressText(op, state, modeText)

        return UploadProgressUiState.UploadItem.Paste(
            id = op.id.toString(),
            name = "$modeText ${op.totalFiles}件",
            description = op.currentFileName ?: op.description,
            state = state,
            canNavigate = true,
            currentFileProgress = currentFileProgress,
            progress = overallProgress,
            progressText = progressText,
            isPausable = state == UploadProgressUiState.UploadState.RUNNING && !op.pauseRequested,
            isResumable = state == UploadProgressUiState.UploadState.PAUSED,
            isCancelable = state == UploadProgressUiState.UploadState.RUNNING,
            pasteCallbacks = object : UploadProgressUiState.PasteCallbacks {
                override fun onPauseClick() {
                    requestPausePasteJob(op)
                }

                override fun onResumeClick() {
                    resumePasteJob(op)
                }

                override fun onCancelClick() {
                    cancelPasteJob(op)
                }
            },
        )
    }

    private fun createPasteProgressText(
        op: OperationRepository.OperationProgress,
        state: UploadProgressUiState.UploadState,
        modeText: String,
    ): String {
        val base = "$modeText ${op.totalFiles}件"
        return when (state) {
            UploadProgressUiState.UploadState.RUNNING -> {
                val notCompleted = op.totalFiles - op.completedFiles - op.unresolvedDuplicateFiles
                val completed = formatFileSize(op.completedBytes)
                val total = formatFileSize(op.totalBytes)
                val failedText = if (op.failedFiles > 0) " ${op.failedFiles}失敗" else ""
                "$base - ${op.completedFiles}完了 ${notCompleted}未完了$failedText ($completed/$total)"
            }
            UploadProgressUiState.UploadState.WAITING_RESOLUTION,
            UploadProgressUiState.UploadState.SUCCEEDED,
            -> {
                if (op.unresolvedDuplicateFiles > 0) {
                    "$base - ${op.completedFiles}完了 ${op.unresolvedDuplicateFiles}重複"
                } else {
                    "$base - ${op.completedFiles}完了"
                }
            }
            UploadProgressUiState.UploadState.FAILED -> {
                "$base - ${op.completedFiles}完了 ${op.failedFiles}失敗"
            }
            else -> base
        }
    }

    private fun createDeleteItem(op: OperationRepository.OperationProgress): UploadProgressUiState.UploadItem {
        val state = mapState(op.status)
        val progress = if (state == UploadProgressUiState.UploadState.RUNNING && op.totalFiles > 0) {
            op.completedFiles.toFloat() / op.totalFiles.toFloat()
        } else {
            null
        }

        val base = "削除 ${op.totalFiles}件"
        val progressText = when (state) {
            UploadProgressUiState.UploadState.RUNNING -> {
                val notCompleted = op.totalFiles - op.completedFiles
                "$base - ${op.completedFiles}完了 ${notCompleted}未完了"
            }
            UploadProgressUiState.UploadState.SUCCEEDED -> {
                "$base - ${op.completedFiles}完了"
            }
            UploadProgressUiState.UploadState.FAILED -> {
                "$base - ${op.completedFiles}完了 ${op.failedFiles}失敗"
            }
            else -> base
        }

        return UploadProgressUiState.UploadItem.Delete(
            id = op.id.toString(),
            name = base,
            description = op.currentFileName ?: op.description,
            state = state,
            canNavigate = true,
            progress = progress,
            progressText = progressText,
        )
    }

    private fun mapState(status: OperationRepository.OperationStatus): UploadProgressUiState.UploadState {
        return when (status) {
            OperationRepository.OperationStatus.ENQUEUED -> UploadProgressUiState.UploadState.ENQUEUED
            OperationRepository.OperationStatus.RUNNING -> UploadProgressUiState.UploadState.RUNNING
            OperationRepository.OperationStatus.PAUSED -> UploadProgressUiState.UploadState.PAUSED
            OperationRepository.OperationStatus.COMPLETED -> UploadProgressUiState.UploadState.SUCCEEDED
            OperationRepository.OperationStatus.FAILED -> UploadProgressUiState.UploadState.FAILED
            OperationRepository.OperationStatus.CANCELLED -> UploadProgressUiState.UploadState.CANCELLED
            OperationRepository.OperationStatus.WAITING_RESOLUTION ->
                UploadProgressUiState.UploadState.WAITING_RESOLUTION
        }
    }

    private fun requestPausePasteJob(op: OperationRepository.OperationProgress) {
        viewModelScope.launch {
            operationRepository.requestPause(op.id)
        }
    }

    private fun cancelPasteJob(op: OperationRepository.OperationProgress) {
        viewModelScope.launch {
            val workerId = op.workerId ?: return@launch
            val uuid = runCatching { UUID.fromString(workerId) }.getOrNull() ?: return@launch
            WorkManager.getInstance(getApplication()).cancelWorkById(uuid)
        }
    }

    private fun resumePasteJob(op: OperationRepository.OperationProgress) {
        viewModelScope.launch {
            val inputData = Data.Builder()
                .putLong(FilePasteWorker.KEY_PASTE_JOB_ID, op.id)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FilePasteWorker>()
                .setInputData(inputData)
                .addTag(FilePasteWorker.TAG_PASTE)
                .build()

            operationRepository.updateStatusAndWorkerId(
                id = op.id,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "paste_job_${op.id}",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToUploadDetail(val workerId: String) : ViewModelEvent
        data class NavigateToPasteDetail(val jobId: Long) : ViewModelEvent
        data class NavigateToDeleteDetail(val opId: Long) : ViewModelEvent
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
}
