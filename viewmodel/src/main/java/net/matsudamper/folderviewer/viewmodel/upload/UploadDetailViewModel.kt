package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Locale
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadDetailUiState
import net.matsudamper.folderviewer.viewmodel.worker.FolderUploadWorker

@HiltViewModel
class UploadDetailViewModel @Inject internal constructor(
    application: Application,
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

            val storageName = storageRepository.storageList.first().find { it.id == job.fileObjectId.storageId }?.name ?: ""

            val workManager = WorkManager.getInstance(getApplication())
            val uuid = runCatching { UUID.fromString(workerId) }.getOrNull()

            if (uuid != null) {
                workManager.getWorkInfoByIdFlow(uuid).collectLatest { workInfo ->
                    val uploadStatus = when (workInfo?.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.BLOCKED,
                            -> UploadDetailUiState.UploadStatus.UPLOADING

                        WorkInfo.State.SUCCEEDED -> UploadDetailUiState.UploadStatus.SUCCEEDED
                        WorkInfo.State.FAILED,
                        WorkInfo.State.CANCELLED,
                            -> UploadDetailUiState.UploadStatus.FAILED

                        null -> UploadDetailUiState.UploadStatus.SUCCEEDED
                    }

                    val currentUploadFile = extractCurrentUploadFile(workInfo, uploadStatus)
                    val progressText = extractProgressText(workInfo, uploadStatus)
                    val progress = extractProgress(workInfo, uploadStatus)

                    _uiState.value = UploadDetailUiState(
                        name = job.name,
                        isFolder = job.isFolder,
                        displayPath = job.displayPath,
                        storageName = storageName,
                        uploadStatus = uploadStatus,
                        errorMessage = job.errorMessage,
                        errorCause = job.errorCause,
                        progressText = progressText,
                        progress = progress,
                        currentUploadFile = currentUploadFile,
                        callbacks = callbacks,
                    )
                }
            } else {
                val hasError = job.errorMessage != null || job.errorCause != null
                _uiState.value = UploadDetailUiState(
                    name = job.name,
                    isFolder = job.isFolder,
                    displayPath = job.displayPath,
                    storageName = storageName,
                    uploadStatus = if (hasError) {
                        UploadDetailUiState.UploadStatus.FAILED
                    } else {
                        UploadDetailUiState.UploadStatus.SUCCEEDED
                    },
                    errorMessage = job.errorMessage,
                    errorCause = job.errorCause,
                    progressText = null,
                    progress = null,
                    currentUploadFile = null,
                    callbacks = callbacks,
                )
            }
        }
    }

    private fun extractCurrentUploadFile(
        workInfo: WorkInfo?,
        uploadStatus: UploadDetailUiState.UploadStatus,
    ): UploadDetailUiState.CurrentUploadFile? {
        if (uploadStatus != UploadDetailUiState.UploadStatus.UPLOADING) return null

        val fileNamesJson = workInfo?.progress?.getString(FolderUploadWorker.KEY_FILE_NAMES)
            ?: return null
        val fileSizesJson = workInfo.progress.getString(FolderUploadWorker.KEY_FILE_SIZES)
            ?: return null

        val fileNames = runCatching {
            Json.decodeFromString<List<String>>(fileNamesJson)
        }.getOrNull() ?: return null
        val fileSizes = runCatching {
            Json.decodeFromString<List<Long?>>(fileSizesJson)
        }.getOrNull() ?: return null

        val currentBytes = workInfo.progress.getLong(FolderUploadWorker.KEY_CURRENT_BYTES, 0L)
        val completedFiles = workInfo.progress.getInt(FolderUploadWorker.KEY_COMPLETED_FILES, 0)

        val cumulativeSize = fileSizes.take(completedFiles).sumOf { it ?: 0L }

        val currentFileName = fileNames.getOrNull(completedFiles) ?: return null
        val currentFileSize = fileSizes.getOrNull(completedFiles)
        val currentFileUploadedBytes = currentBytes - cumulativeSize

        val progress = if (currentFileSize != null && currentFileSize > 0L) {
            (currentFileUploadedBytes.toFloat() / currentFileSize.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

        val progressText = if (currentFileSize != null && currentFileSize > 0L) {
            "${formatFileSize(currentFileUploadedBytes)}/${formatFileSize(currentFileSize)}"
        } else {
            null
        }

        return UploadDetailUiState.CurrentUploadFile(
            name = currentFileName,
            progressText = progressText,
            progress = progress,
        )
    }

    private fun extractProgressText(
        workInfo: WorkInfo?,
        uploadStatus: UploadDetailUiState.UploadStatus,
    ): String? {
        if (uploadStatus != UploadDetailUiState.UploadStatus.UPLOADING) return null
        val currentBytes = workInfo?.progress?.getLong(FolderUploadWorker.KEY_CURRENT_BYTES, 0L) ?: return null
        val totalBytes = workInfo.progress.getLong("TotalBytes", 0L)
        if (totalBytes <= 0L) return null
        return "${formatFileSize(currentBytes)}/${formatFileSize(totalBytes)}"
    }

    private fun extractProgress(
        workInfo: WorkInfo?,
        uploadStatus: UploadDetailUiState.UploadStatus,
    ): Float? {
        if (uploadStatus != UploadDetailUiState.UploadStatus.UPLOADING) return null
        val currentBytes = workInfo?.progress?.getLong(FolderUploadWorker.KEY_CURRENT_BYTES, 0L) ?: return null
        val totalBytes = workInfo.progress.getLong("TotalBytes", 0L)
        if (totalBytes <= 0L) return null
        return (currentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
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
