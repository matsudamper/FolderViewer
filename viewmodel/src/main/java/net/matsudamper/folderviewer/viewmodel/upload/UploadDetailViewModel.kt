package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.OperationFileFilter
import net.matsudamper.folderviewer.ui.upload.OperationFileRow
import net.matsudamper.folderviewer.ui.upload.OperationFileStatus
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

    private val filterState = MutableStateFlow(FileFilterState())

    private var initJob: Job? = null

    fun init(workerId: String) {
        if (initJob?.isActive == true) return
        initJob = viewModelScope.launch {
            val job = uploadJobRepository.getJob(workerId) ?: return@launch
            fileObjectId = job.fileObjectId
            displayPath = job.displayPath

            val storageName = storageRepository.storageList.first()
                .find { it.id == job.fileObjectId.storageId }?.name ?: ""

            combine(
                operationRepository.observeProgressById(job.operationId),
                uploadJobRepository.observeFiles(job.operationId),
                filterState,
            ) { progress, files, filter ->
                if (progress == null) return@combine null
                createUiState(job, storageName, progress, files, filter)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun createUiState(
        job: UploadJobRepository.UploadJob,
        storageName: String,
        progress: OperationRepository.OperationProgress,
        files: List<UploadJobRepository.UploadFile>,
        filter: FileFilterState,
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

        val fileRows = files.map { file ->
            val path = if (file.relativePath.isEmpty()) {
                "${job.displayPath}/${file.fileName}"
            } else {
                "${job.displayPath}/${file.relativePath}/${file.fileName}"
            }
            OperationFileRow(
                key = file.id.toString(),
                fileName = file.fileName,
                sourcePath = null,
                destinationPath = path,
                status = file.status.toFileStatus(),
                errorMessage = file.errorMessage,
            )
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
            files = fileRows,
            fileFilter = filter.toUiFilter(),
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

    private data class FileFilterState(
        val showCompleted: Boolean = true,
        val showPending: Boolean = true,
        val showFailed: Boolean = true,
    )

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToDirectory(
            val fileObjectId: FileObjectId,
            val displayPath: String,
        ) : ViewModelEvent
    }
}
