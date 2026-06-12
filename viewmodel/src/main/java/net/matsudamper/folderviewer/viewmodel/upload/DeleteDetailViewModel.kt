package net.matsudamper.folderviewer.viewmodel.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.upload.DeleteDetailUiState

@HiltViewModel
class DeleteDetailViewModel @Inject constructor(
    private val operationRepository: OperationRepository,
    private val deleteJobRepository: DeleteJobRepository,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<DeleteDetailUiState?>(null)
    val uiState: StateFlow<DeleteDetailUiState?> = _uiState.asStateFlow()

    private var initJob: Job? = null

    fun init(operationId: Long) {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            combine(
                operationRepository.observeProgressById(operationId),
                deleteJobRepository.observeFiles(operationId),
            ) { progress, files ->
                if (progress == null) return@combine null

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
                    OperationRepository.OperationStatus.FAILED -> DeleteDetailUiState.Status.FAILED
                    else -> DeleteDetailUiState.Status.RUNNING
                }

                val failedItems = files
                    .filter { it.status == OperationRepository.FileStatus.FAILED }
                    .map { file ->
                        DeleteDetailUiState.FailedFileItem(
                            fileName = file.fileName,
                            path = file.displayPath(),
                            errorMessage = file.errorMessage ?: "",
                        )
                    }

                val completedItems = files
                    .filter { it.status == OperationRepository.FileStatus.COMPLETED && !it.isDirectory }
                    .map { file ->
                        DeleteDetailUiState.CompletedFileItem(
                            fileName = file.fileName,
                            path = file.displayPath(),
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
                    jobName = progress.name,
                    statusText = statusText,
                    status = uiStatus,
                    totalFiles = progress.totalFiles,
                    completedFiles = progress.completedFiles,
                    failedFiles = progress.failedFiles,
                    errorMessage = progress.errorMessage,
                    errorCause = progress.errorCause,
                    failedFileItems = failedItems,
                    completedFileItems = completedItems,
                    callbacks = callbacks,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun DeleteJobRepository.DeleteFile.displayPath(): String {
        return if (relativePath.isEmpty()) {
            fileName
        } else {
            "$relativePath/$fileName"
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
