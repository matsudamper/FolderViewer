package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadDetailUiState

@HiltViewModel
class UploadDetailViewModel @Inject internal constructor(
    application: Application,
    private val operationRepository: OperationRepository,
    private val uploadJobRepository: UploadJobRepository,
    private val storageRepository: StorageRepository,
) : AndroidViewModel(application) {
    private var fileObjectId: FileObjectId? = null
    private var displayPath: String? = null

    private val callbacks = object : UploadDetailUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }

        override fun onNavigateToDirectoryClick() {
            viewModelScope.launch {
                val fid = fileObjectId ?: return@launch
                val dp = displayPath ?: return@launch
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToDirectory(
                        fileObjectId = fid,
                        displayPath = dp,
                    ),
                )
            }
        }
    }

    private val _uiState = MutableStateFlow<UploadDetailUiState?>(null)
    val uiState: StateFlow<UploadDetailUiState?> = _uiState

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.BUFFERED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    fun init(workerId: String) {
        viewModelScope.launch {
            val job = uploadJobRepository.getJob(workerId) ?: return@launch

            fileObjectId = job.fileObjectId
            displayPath = job.displayPath

            val storageName = storageRepository.storageList.first()
                .find { it.id == job.fileObjectId.storageId }?.name ?: ""

            operationRepository.observeProgressByWorkerId(workerId).collect { progress ->
                if (progress == null) return@collect
                _uiState.value = createUiState(job, storageName, progress)
            }
        }
    }

    private fun createUiState(
        job: UploadJobRepository.UploadJob,
        storageName: String,
        progress: OperationRepository.OperationProgress,
    ): UploadDetailUiState {
        val uploadStatus = when (progress.status) {
            OperationRepository.OperationStatus.ENQUEUED,
            OperationRepository.OperationStatus.RUNNING,
            OperationRepository.OperationStatus.PAUSED,
            OperationRepository.OperationStatus.WAITING_RESOLUTION,
            -> UploadDetailUiState.UploadStatus.UPLOADING

            OperationRepository.OperationStatus.COMPLETED -> UploadDetailUiState.UploadStatus.SUCCEEDED

            OperationRepository.OperationStatus.FAILED,
            OperationRepository.OperationStatus.CANCELLED,
            -> UploadDetailUiState.UploadStatus.FAILED
        }

        val isUploading = uploadStatus == UploadDetailUiState.UploadStatus.UPLOADING
        val hasTotalBytes = progress.totalBytes > 0
        val progressText = if (isUploading && hasTotalBytes) {
            "${formatFileSize(progress.completedBytes)}/${formatFileSize(progress.totalBytes)}"
        } else {
            null
        }
        val overallProgress = if (isUploading && hasTotalBytes) {
            (progress.completedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

        return UploadDetailUiState(
            name = job.name,
            isFolder = job.isFolder,
            displayPath = job.displayPath,
            storageName = storageName,
            uploadStatus = uploadStatus,
            errorMessage = progress.errorMessage,
            errorCause = progress.errorCause,
            progressText = progressText,
            progress = overallProgress,
            currentUploadFile = createCurrentUploadFile(isUploading, progress),
            callbacks = callbacks,
        )
    }

    private fun createCurrentUploadFile(
        isUploading: Boolean,
        progress: OperationRepository.OperationProgress,
    ): UploadDetailUiState.CurrentUploadFile? {
        if (!isUploading) return null
        val currentFileName = progress.currentFileName ?: return null

        val currentFileBytes = progress.currentFileBytes ?: 0L
        val currentFileTotalBytes = progress.currentFileTotalBytes
        return if (currentFileTotalBytes != null && currentFileTotalBytes > 0) {
            UploadDetailUiState.CurrentUploadFile(
                name = currentFileName,
                progressText = "${formatFileSize(currentFileBytes)}/${formatFileSize(currentFileTotalBytes)}",
                progress = (currentFileBytes.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f),
            )
        } else {
            UploadDetailUiState.CurrentUploadFile(
                name = currentFileName,
                progressText = null,
                progress = null,
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
        data class NavigateToDirectory(
            val fileObjectId: FileObjectId,
            val displayPath: String,
        ) : ViewModelEvent
    }
}
