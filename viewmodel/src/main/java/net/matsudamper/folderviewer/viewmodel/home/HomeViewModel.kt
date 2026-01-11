package net.matsudamper.folderviewer.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.home.HomeUiState
import net.matsudamper.folderviewer.ui.home.UiStorageConfiguration

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    private val callbacks: HomeUiState.Callbacks = object : HomeUiState.Callbacks {
        override fun onNavigateToSettings() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateToSettings)
            }
        }

        override fun onAddStorageClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateToStorageTypeSelection)
            }
        }

        override fun onStorageClick(storage: UiStorageConfiguration) {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateToFileBrowser(storage.id))
            }
        }

        override fun onEditStorageClick(storage: UiStorageConfiguration) {
            when (storage) {
                is UiStorageConfiguration.Smb -> {
                    viewModelScope.launch {
                        viewModelEventChannel.send(ViewModelEvent.NavigateToSmbAdd(storage.id))
                    }
                }

                is UiStorageConfiguration.Local -> {
                    // 何もしない
                }

                is UiStorageConfiguration.SharePoint -> {
                    viewModelScope.launch {
                        viewModelEventChannel.send(ViewModelEvent.NavigateToSharePointAdd(storage.id))
                    }
                }
            }
        }

        override fun onDeleteStorageClick(id: String) {
            onDeleteStorage(id)
        }
    }

    val uiState: StateFlow<HomeUiState> = MutableStateFlow(
        HomeUiState(
            storages = emptyList(),
            callbacks = callbacks,
        ),
    ).also { mutableUiState ->
        viewModelScope.launch {
            viewModelStateFlow.collect { viewModelState ->
                mutableUiState.update {
                    it.copy(
                        storages = viewModelState.storages.map { storage ->
                            when (storage) {
                                is StorageConfiguration.Smb -> {
                                    UiStorageConfiguration.Smb(
                                        id = storage.id,
                                        name = storage.name,
                                        ip = storage.ip,
                                        username = storage.username,
                                    )
                                }

                                is StorageConfiguration.Local -> {
                                    UiStorageConfiguration.Local(
                                        id = storage.id,
                                        name = storage.name,
                                        rootPath = storage.rootPath,
                                    )
                                }

                                is StorageConfiguration.SharePoint -> {
                                    UiStorageConfiguration.SharePoint(
                                        id = storage.id,
                                        name = storage.name,
                                        objectId = storage.objectId,
                                    )
                                }
                            }
                        },
                        callbacks = callbacks,
                    )
                }
            }
        }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            storageRepository.storageList.collect { storages ->
                viewModelStateFlow.update {
                    it.copy(storages = storages)
                }
            }
        }
    }

    private fun onDeleteStorage(id: String) {
        viewModelScope.launch {
            storageRepository.deleteStorage(id)
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateToSettings : ViewModelEvent
        data object NavigateToStorageTypeSelection : ViewModelEvent
        data class NavigateToFileBrowser(val storageId: String) : ViewModelEvent
        data class NavigateToSmbAdd(val storageId: String) : ViewModelEvent
        data class NavigateToSharePointAdd(val storageId: String) : ViewModelEvent
    }

    private data class ViewModelState(
        val storages: List<StorageConfiguration> = emptyList(),
    )
}
