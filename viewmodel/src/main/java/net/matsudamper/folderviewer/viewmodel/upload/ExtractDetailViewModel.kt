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
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.upload.ExtractDetailUiState
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputLocationResolver

@HiltViewModel
class ExtractDetailViewModel @Inject constructor(
    private val operationRepository: OperationRepository,
    private val extractJobRepository: ExtractJobRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow<ExtractDetailUiState?>(null)
    val uiState: StateFlow<ExtractDetailUiState?> = _uiState.asStateFlow()

    private var initJob: Job? = null
    private var currentOperationId: Long? = null

    private val callbacks = object : ExtractDetailUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }

        override fun onNavigateToOutputClick() {
            val operationId = currentOperationId ?: return
            viewModelScope.launch {
                navigateToOutput(operationId)
            }
        }

        override fun onOpenOutputFileClick() {
            val operationId = currentOperationId ?: return
            viewModelScope.launch {
                openOutputFile(operationId)
            }
        }
    }

    fun init(operationId: Long) {
        if (initJob?.isActive == true && currentOperationId == operationId) {
            return
        }
        currentOperationId = operationId
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

    private suspend fun createUiState(
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
        val isCompleted = uiStatus == ExtractDetailUiState.Status.COMPLETED
        val canNavigateToOutput = isCompleted && meta != null && (
            ExtractOutputLocationResolver.resolveNavigateToOutput(meta, storageRepository) != null ||
                ExtractOutputLocationResolver.resolveOpenOutputFolder(meta, storageRepository) != null
            )
        val canOpenOutputFile = isCompleted && meta != null &&
            ExtractOutputLocationResolver.resolveOpenOutputFile(meta, storageRepository) != null

        return ExtractDetailUiState(
            jobName = progress.name,
            statusText = statusText,
            status = uiStatus,
            sourceFile = sourceFile,
            outputName = meta?.outputName ?: progress.name,
            outputPath = outputPath,
            canNavigateToOutput = canNavigateToOutput,
            canOpenOutputFile = canOpenOutputFile,
            extractTypeLabel = meta?.extractType.toLabel(),
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            callbacks = callbacks,
        )
    }

    private suspend fun navigateToOutput(operationId: Long) {
        val meta = extractJobRepository.getJobMeta(operationId) ?: return
        ExtractOutputLocationResolver.resolveNavigateToOutput(meta, storageRepository)?.let { target ->
            viewModelEventChannel.send(
                ViewModelEvent.NavigateToOutput(
                    fileId = target.fileId,
                    displayPath = target.displayPath,
                ),
            )
            return
        }
        ExtractOutputLocationResolver.resolveOpenOutputFolder(meta, storageRepository)?.let { target ->
            viewModelEventChannel.send(ViewModelEvent.OpenOutputFolder(target.absolutePath))
        }
    }

    private suspend fun openOutputFile(operationId: Long) {
        val meta = extractJobRepository.getJobMeta(operationId) ?: return
        val target = ExtractOutputLocationResolver.resolveOpenOutputFile(meta, storageRepository) ?: return
        viewModelEventChannel.send(
            ViewModelEvent.OpenOutputFile(
                viewSourceUri = target.viewSourceUri,
                fileName = target.fileName,
                mimeType = target.mimeType,
            ),
        )
    }

    private fun ExtractJobRepository.ExtractType?.toLabel(): String {
        return when (this) {
            ExtractJobRepository.ExtractType.Zip -> "ZIP（フォルダ）"
            ExtractJobRepository.ExtractType.TarXz -> "tar.xz"
            ExtractJobRepository.ExtractType.TarZst -> "tar.zst"
            ExtractJobRepository.ExtractType.Zst -> "Zstandard（.zst）"
            ExtractJobRepository.ExtractType.Xz -> "XZ（.xz）"
            null -> "不明"
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent

        data class NavigateToOutput(
            val fileId: FileObjectId,
            val displayPath: String?,
        ) : ViewModelEvent

        data class OpenOutputFile(
            val viewSourceUri: ViewSourceUri,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent

        data class OpenOutputFolder(
            val absolutePath: String,
        ) : ViewModelEvent
    }
}
