package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadDetailUiState

@HiltViewModel
class UploadDetailViewModel @Inject internal constructor(
    application: Application,
    private val uploadJobRepository: UploadJobRepository,
    private val storageRepository: StorageRepository,
) : AndroidViewModel(application) {
    private var storageId: StorageId? = null
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
                val sid = storageId ?: return@launch
                val fid = fileObjectId ?: return@launch
                val dp = displayPath ?: return@launch
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToDirectory(
                        storageId = sid,
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

            storageId = job.storageId
            fileObjectId = job.fileObjectId
            displayPath = job.displayPath

            val storageName = storageRepository.storageList.first().find { it.id == job.storageId }?.name ?: ""

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

                    _uiState.value = UploadDetailUiState(
                        name = job.name,
                        isFolder = job.isFolder,
                        displayPath = job.displayPath,
                        storageName = storageName,
                        uploadStatus = uploadStatus,
                        errorMessage = job.errorMessage,
                        errorCause = job.errorCause,
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
                    callbacks = callbacks,
                )
            }
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToDirectory(
            val storageId: StorageId,
            val fileObjectId: FileObjectId,
            val displayPath: String,
        ) : ViewModelEvent
    }
}
