package net.matsudamper.folderviewer.viewmodel.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.UploadErrorDetailUiState

@HiltViewModel
class UploadErrorDetailViewModel @Inject internal constructor(
    private val uploadJobRepository: UploadJobRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val callbacks = object : UploadErrorDetailUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }
    }

    private val _uiState = MutableStateFlow<UploadErrorDetailUiState?>(null)
    val uiState: StateFlow<UploadErrorDetailUiState?> = _uiState

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.BUFFERED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    fun init(workerId: String) {
        viewModelScope.launch {
            val job = uploadJobRepository.getJob(workerId) ?: return@launch

            val storageName = storageRepository.storageList.first().find { it.id == job.storageId }?.name ?: ""

            _uiState.value = UploadErrorDetailUiState(
                name = job.name,
                isFolder = job.isFolder,
                displayPath = job.displayPath,
                storageName = storageName,
                errorMessage = job.errorMessage,
                errorCause = job.errorCause,
                callbacks = callbacks,
            )
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
