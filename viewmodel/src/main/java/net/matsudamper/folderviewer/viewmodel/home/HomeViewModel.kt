package net.matsudamper.folderviewer.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    storageRepository: StorageRepository,
) : ViewModel() {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    val uiState: StateFlow<HomeUiState> = MutableStateFlow(
        HomeUiState(
            storages = emptyList(),
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
                            }
                        },
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

    private data class ViewModelState(
        val storages: List<StorageConfiguration> = emptyList(),
    )
}
