package net.matsudamper.folderviewer.viewmodel.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.navigation.SharePointAdd
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.storage.SharePointAddUiState
import net.matsudamper.folderviewer.ui.storage.SharePointInput

@HiltViewModel(assistedFactory = SharePointAddViewModel.Companion.Factory::class)
class SharePointAddViewModel @AssistedInject constructor(
    private val storageRepository: StorageRepository,
    @Assisted private val arg: SharePointAdd,
) : ViewModel() {
    private val storageId: StorageId? = arg.storageId

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    private val callbacks: SharePointAddUiState.Callbacks = object : SharePointAddUiState.Callbacks {
        override fun onSave(input: SharePointInput) {
            onSaveInternal(input)
        }

        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }
    }

    val uiState: StateFlow<SharePointAddUiState> =
        MutableStateFlow(
            SharePointAddUiState(
                name = "",
                objectId = "",
                tenantId = "",
                clientId = "",
                clientSecret = "",
                isEditMode = false,
                isLoading = false,
                callbacks = callbacks,
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            name = viewModelState.name,
                            objectId = viewModelState.objectId,
                            tenantId = viewModelState.tenantId,
                            clientId = viewModelState.clientId,
                            clientSecret = viewModelState.clientSecret,
                            isEditMode = viewModelState.isEditMode,
                            isLoading = viewModelState.isLoading,
                            callbacks = callbacks,
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
                    .find { it.id == storageId } as? StorageConfiguration.SharePoint
                if (storage != null) {
                    viewModelStateFlow.update {
                        it.copy(
                            name = storage.name,
                            objectId = storage.objectId,
                            tenantId = storage.tenantId,
                            clientId = storage.clientId,
                            clientSecret = storage.clientSecret,
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

    private fun onSaveInternal(input: SharePointInput) {
        val repoInput = StorageRepository.SharePointStorageInput(
            name = input.name,
            objectId = input.objectId,
            tenantId = input.tenantId,
            clientId = input.clientId,
            clientSecret = input.clientSecret,
        )
        viewModelScope.launch {
            if (storageId == null) {
                storageRepository.addSharePointStorage(repoInput)
            } else {
                storageRepository.updateSharePointStorage(storageId, repoInput)
            }
            viewModelEventChannel.send(ViewModelEvent.SaveSuccess)
        }
    }

    sealed interface ViewModelEvent {
        data object SaveSuccess : ViewModelEvent
        data object NavigateBack : ViewModelEvent
    }

    private data class ViewModelState(
        val name: String = "",
        val objectId: String = "",
        val tenantId: String = "",
        val clientId: String = "",
        val clientSecret: String = "",
        val isEditMode: Boolean = false,
        val isLoading: Boolean = false,
    )

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: SharePointAdd): SharePointAddViewModel
        }
    }
}
