package net.matsudamper.folderviewer.viewmodel.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.upload.DeleteDetailUiState

@HiltViewModel
class DeleteDetailViewModel @Inject constructor(
    private val deleteJobRepository: DeleteJobRepository,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<DeleteDetailUiState?>(null)
    val uiState: StateFlow<DeleteDetailUiState?> = _uiState.asStateFlow()

    fun init(operationId: Long) {
        viewModelScope.launch {
            combine(
                deleteJobRepository.observeJob(operationId),
                deleteJobRepository.observeFiles(operationId),
            ) { job, files ->
                if (job == null) return@combine null

                val statusText = when (job.status) {
                    OperationRepository.OperationStatus.ENQUEUED -> "待機中"
                    OperationRepository.OperationStatus.RUNNING -> "削除中"
                    OperationRepository.OperationStatus.PAUSED -> "一時停止"
                    OperationRepository.OperationStatus.COMPLETED -> "完了"
                    OperationRepository.OperationStatus.FAILED -> "失敗"
                    OperationRepository.OperationStatus.CANCELLED -> "キャンセル"
                    OperationRepository.OperationStatus.WAITING_RESOLUTION -> "確認待ち"
                }

                val uiStatus = when (job.status) {
                    OperationRepository.OperationStatus.COMPLETED -> DeleteDetailUiState.Status.COMPLETED
                    OperationRepository.OperationStatus.FAILED -> DeleteDetailUiState.Status.FAILED
                    OperationRepository.OperationStatus.RUNNING -> DeleteDetailUiState.Status.RUNNING
                    else -> DeleteDetailUiState.Status.RUNNING
                }

                val failedItems = files
                    .filter { it.errorMessage != null }
                    .map { file ->
                        DeleteDetailUiState.FailedFileItem(
                            fileName = file.fileName,
                            path = if (file.parentRelativePath.isEmpty()) file.fileName else "${file.parentRelativePath}/${file.fileName}",
                            errorMessage = file.errorMessage ?: "",
                        )
                    }

                val completedItems = files
                    .filter { it.completed && it.errorMessage == null && !it.isDirectory }
                    .map { file ->
                        DeleteDetailUiState.CompletedFileItem(
                            fileName = file.fileName,
                            path = if (file.parentRelativePath.isEmpty()) file.fileName else "${file.parentRelativePath}/${file.fileName}",
                        )
                    }

                val callbacks = object : DeleteDetailUiState.Callbacks {
                    override fun onBackClick() {
                        viewModelScope.launch {
                            viewModelEventChannel.send(ViewModelEvent.NavigateBack)
                        }
                    }
                }

                DeleteDetailUiState(
                    jobName = job.name,
                    statusText = statusText,
                    status = uiStatus,
                    totalFiles = job.totalFiles,
                    completedFiles = job.completedFiles,
                    failedFiles = job.failedFiles,
                    errorMessage = job.errorMessage,
                    errorCause = job.errorCause,
                    failedFileItems = failedItems,
                    completedFileItems = completedItems,
                    callbacks = callbacks,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
