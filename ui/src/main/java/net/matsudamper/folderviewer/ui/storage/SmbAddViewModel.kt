package net.matsudamper.folderviewer.ui.storage

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
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltViewModel
class SmbAddViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val storageId: String? = savedStateHandle["storageId"]

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val name: String = "",
        val ip: String = "",
        val username: String = "",
        val password: String = "",
        val isEditMode: Boolean = false,
        val isLoading: Boolean = false,
    )

    init {
        if (storageId != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val storage = storageRepository.storageList.first()
                    .find { it.id == storageId } as? StorageConfiguration.Smb
                if (storage != null) {
                    _uiState.value = UiState(
                        name = storage.name,
                        ip = storage.ip,
                        username = storage.username,
                        password = storageRepository.getPassword(storage.id) ?: "",
                        isEditMode = true,
                        isLoading = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun onSave(name: String, ip: String, username: String, password: String) {
        viewModelScope.launch {
            if (storageId == null) {
                storageRepository.addSmbStorage(name, ip, username, password)
            } else {
                storageRepository.updateSmbStorage(storageId, name, ip, username, password)
            }
            _event.send(Event.SaveSuccess)
        }
    }

    sealed interface Event {
        data object SaveSuccess : Event
    }
}
