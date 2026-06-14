package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.upload.DeleteDetailUiState
import net.matsudamper.folderviewer.ui.upload.OperationFileFilter
import net.matsudamper.folderviewer.ui.upload.OperationFileRow
import net.matsudamper.folderviewer.ui.upload.OperationFileStatus
import net.matsudamper.folderviewer.viewmodel.worker.FileDeleteWorker

@HiltViewModel
class DeleteDetailViewModel @Inject constructor(
    application: Application,
    private val operationRepository: OperationRepository,
    private val deleteJobRepository: DeleteJobRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<DeleteDetailUiState?>(null)
    val uiState: StateFlow<DeleteDetailUiState?> = _uiState.asStateFlow()

    private val filterState = MutableStateFlow(FileFilterState())

    private var initJob: Job? = null

    fun init(operationId: Long) {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            combine(
                operationRepository.observeProgressById(operationId),
                deleteJobRepository.observeFiles(operationId),
                filterState,
            ) { progress, files, filter ->
                if (progress == null) return@combine null
                createUiState(operationId, progress, files, filter)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun createUiState(
        operationId: Long,
        progress: OperationRepository.OperationProgress,
        files: List<DeleteJobRepository.DeleteFile>,
        filter: FileFilterState,
    ): DeleteDetailUiState {
        val statusText = when (progress.status) {
            OperationRepository.OperationStatus.ENQUEUED -> "待機中"
            OperationRepository.OperationStatus.RUNNING -> "削除中"
            OperationRepository.OperationStatus.PAUSED -> "一時停止"
            OperationRepository.OperationStatus.COMPLETED -> "完了"
            OperationRepository.OperationStatus.FAILED -> "失敗"
            OperationRepository.OperationStatus.CANCELLED -> "キャンセル"
            OperationRepository.OperationStatus.WAITING_RESOLUTION -> "確認待ち"
        }

        val uiStatus = when (progress.status) {
            OperationRepository.OperationStatus.COMPLETED -> DeleteDetailUiState.Status.COMPLETED
            OperationRepository.OperationStatus.FAILED,
            OperationRepository.OperationStatus.CANCELLED,
            -> DeleteDetailUiState.Status.FAILED
            else -> DeleteDetailUiState.Status.RUNNING
        }

        val fileRows = files.map { file ->
            OperationFileRow(
                key = file.id.toString(),
                fileName = file.fileName,
                sourcePath = null,
                destinationPath = file.displayPath(),
                status = file.status.toFileStatus(),
                errorMessage = file.errorMessage,
            )
        }

        val canRetry = uiStatus == DeleteDetailUiState.Status.FAILED && progress.failedFiles > 0

        return DeleteDetailUiState(
            jobName = progress.name,
            statusText = statusText,
            status = uiStatus,
            totalFiles = progress.totalFiles,
            completedFiles = progress.completedFiles,
            failedFiles = progress.failedFiles,
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            files = fileRows,
            fileFilter = filter.toUiFilter(),
            canRetry = canRetry,
            callbacks = object : DeleteDetailUiState.Callbacks {
                override fun onBackClick() {
                    viewModelScope.launch {
                        viewModelEventChannel.send(ViewModelEvent.NavigateBack)
                    }
                }

                override fun onRetryClick() {
                    retry(operationId)
                }
            },
        )
    }

    private fun retry(operationId: Long) {
        viewModelScope.launch {
            deleteJobRepository.retryJob(operationId)

            val inputData = Data.Builder()
                .putLong(FileDeleteWorker.KEY_DELETE_OPERATION_ID, operationId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<FileDeleteWorker>()
                .setInputData(inputData)
                .addTag(FileDeleteWorker.TAG_DELETE)
                .build()

            deleteJobRepository.updateStatus(
                operationId = operationId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "delete_job_$operationId",
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

    private fun DeleteJobRepository.DeleteFile.displayPath(): String {
        return if (relativePath.isEmpty()) {
            fileName
        } else {
            "$relativePath/$fileName"
        }
    }

    private data class FileFilterState(
        val showCompleted: Boolean = true,
        val showPending: Boolean = true,
        val showFailed: Boolean = true,
    )

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
