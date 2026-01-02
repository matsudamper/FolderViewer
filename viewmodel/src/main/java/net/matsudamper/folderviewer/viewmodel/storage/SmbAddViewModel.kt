package net.matsudamper.folderviewer.viewmodel.storage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.storage.SmbAddUiState
import net.matsudamper.folderviewer.ui.storage.SmbInput

@HiltViewModel
class SmbAddViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val storageId: String? = savedStateHandle["storageId"]

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    val uiState: StateFlow<SmbAddUiState> =
        MutableStateFlow(
            SmbAddUiState(
                name = "",
                ip = "",
                username = "",
                password = "",
                isEditMode = false,
                isLoading = false,
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            name = viewModelState.name,
                            ip = viewModelState.ip,
                            username = viewModelState.username,
                            password = viewModelState.password,
                            isEditMode = viewModelState.isEditMode,
                            isLoading = viewModelState.isLoading,
                        )
                    }
                }
            }
        }.asStateFlow()

    init {
        if (storageId != null) {
            viewModelScope.launch {
                viewModelStateFlow.update { it.copy(isLoading = true) }
                val storage = storageRepository.storageList.first()
                    .find { it.id == storageId } as? StorageConfiguration.Smb
                if (storage != null) {
                    viewModelStateFlow.update {
                        it.copy(
                            name = storage.name,
                            ip = storage.ip,
                            username = storage.username,
                            password = storage.password,
                            isEditMode = true,
                            isLoading = false,
                        )
                    }
                } else {
                    viewModelStateFlow.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onSave(input: SmbInput) {
        val repoInput = StorageRepository.SmbStorageInput(
            name = input.name,
            ip = input.ip,
            username = input.username,
            password = input.password,
        )
        viewModelScope.launch {
            if (storageId == null) {
                storageRepository.addSmbStorage(repoInput)
            } else {
                storageRepository.updateSmbStorage(storageId, repoInput)
            }
            viewModelEventChannel.send(ViewModelEvent.SaveSuccess)
        }
    }

    sealed interface ViewModelEvent {
        data object SaveSuccess : ViewModelEvent
    }

    private data class ViewModelState(
        val name: String = "",
        val ip: String = "",
        val username: String = "",
        val password: String = "",
        val isEditMode: Boolean = false,
        val isLoading: Boolean = false,
    )
}
