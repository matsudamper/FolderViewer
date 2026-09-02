package net.matsudamper.folderviewer.viewmodel.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.upload.ExtractDetailUiState

@HiltViewModel
class ExtractDetailViewModel @Inject constructor(
    private val operationRepository: OperationRepository,
    private val extractJobRepository: ExtractJobRepository,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<ExtractDetailUiState?>(null)
    val uiState: StateFlow<ExtractDetailUiState?> = _uiState.asStateFlow()

    private var initJob: Job? = null

    fun init(operationId: Long) {
        if (initJob?.isActive == true) {
            return
        }
        initJob = viewModelScope.launch {
            operationRepository.observeProgressById(operationId).collect { progress ->
                if (progress == null) {
                    _uiState.value = null
                    return@collect
                }
                val meta = extractJobRepository.getJobMeta(operationId)
                _uiState.value = createUiState(progress, meta)
            }
        }
    }

    private fun createUiState(
        progress: OperationRepository.OperationProgress,
        meta: ExtractJobRepository.ExtractJobMeta?,
    ): ExtractDetailUiState {
        val statusText = when (progress.status) {
            OperationRepository.OperationStatus.ENQUEUED -> "待機中"
            OperationRepository.OperationStatus.RUNNING -> "解凍中"
            OperationRepository.OperationStatus.PAUSED -> "一時停止"
            OperationRepository.OperationStatus.COMPLETED -> "完了"
            OperationRepository.OperationStatus.FAILED -> "失敗"
            OperationRepository.OperationStatus.CANCELLED -> "キャンセル"
            OperationRepository.OperationStatus.WAITING_RESOLUTION -> "確認待ち"
        }

        val uiStatus = when (progress.status) {
            OperationRepository.OperationStatus.COMPLETED -> ExtractDetailUiState.Status.COMPLETED
            OperationRepository.OperationStatus.FAILED -> ExtractDetailUiState.Status.FAILED
            OperationRepository.OperationStatus.CANCELLED -> ExtractDetailUiState.Status.CANCELLED
            else -> ExtractDetailUiState.Status.RUNNING
        }

        val sourceFile = meta?.sourceAbsolutePath ?: meta?.sourceFileName ?: progress.description
        val outputPath = meta?.outputAbsolutePath
            ?: meta?.let { File(it.localFolderPath, it.outputName).absolutePath }

        return ExtractDetailUiState(
            jobName = progress.name,
            statusText = statusText,
            status = uiStatus,
            sourceFile = sourceFile,
            outputName = meta?.outputName ?: progress.name,
            outputPath = outputPath,
            extractTypeLabel = meta?.extractType.toLabel(),
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            callbacks = object : ExtractDetailUiState.Callbacks {
                override fun onBackClick() {
                    viewModelScope.launch {
                        viewModelEventChannel.send(ViewModelEvent.NavigateBack)
                    }
                }
            },
        )
    }

    private fun ExtractJobRepository.ExtractType?.toLabel(): String {
        return when (this) {
            ExtractJobRepository.ExtractType.Zip -> "ZIP（フォルダ）"
            ExtractJobRepository.ExtractType.Zst -> "Zstandard（.zst）"
            ExtractJobRepository.ExtractType.Xz -> "XZ（.xz）"
            null -> "不明"
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
