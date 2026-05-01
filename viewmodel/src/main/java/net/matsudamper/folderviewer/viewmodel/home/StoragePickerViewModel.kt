package net.matsudamper.folderviewer.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.home.StoragePickerUiState
import net.matsudamper.folderviewer.ui.home.UiStorageConfiguration

@HiltViewModel
class StoragePickerViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val callbacks = object : StoragePickerUiState.Callbacks {
        override fun onStorageClick(storage: UiStorageConfiguration) {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateToFileBrowser(storage.id))
            }
        }
    }

    val uiState: StateFlow<StoragePickerUiState> = MutableStateFlow(
        StoragePickerUiState(storages = emptyList(), callbacks = callbacks),
    ).also { mutableUiState ->
        viewModelScope.launch {
            storageRepository.storageList.collect { storages ->
                mutableUiState.update {
                    it.copy(storages = storages.map { config -> config.toUiModel() })
                }
            }
        }
    }.asStateFlow()

    private fun StorageConfiguration.toUiModel(): UiStorageConfiguration {
        return when (this) {
            is StorageConfiguration.Smb -> UiStorageConfiguration.Smb(
                id = id,
                name = name,
                ip = ip,
                username = username,
            )

            is StorageConfiguration.Local -> UiStorageConfiguration.Local(
                id = id,
                name = name,
                rootPath = rootPath,
            )

            is StorageConfiguration.SharePoint -> UiStorageConfiguration.SharePoint(
                id = id,
                name = name,
                objectId = objectId,
            )
        }
    }

    sealed interface ViewModelEvent {
        data class NavigateToFileBrowser(val storageId: StorageId) : ViewModelEvent
    }
}
