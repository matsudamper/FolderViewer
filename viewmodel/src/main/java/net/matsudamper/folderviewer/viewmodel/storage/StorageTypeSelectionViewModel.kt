package net.matsudamper.folderviewer.viewmodel.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.PermissionUtil
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltViewModel
class StorageTypeSelectionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    fun onLocalClick() {
        viewModelScope.launch {
            if (PermissionUtil.hasManageExternalStoragePermission(context)) {
                val existingStorages = storageRepository.storageList.first()
                val hasLocalStorage = existingStorages.any { it is StorageConfiguration.Local }

                if (hasLocalStorage) {
                    viewModelEventChannel.send(ViewModelEvent.ShowAlreadyAddedMessage)
                } else {
                    storageRepository.detectLocalStorages()
                    viewModelEventChannel.send(ViewModelEvent.NavigateToHome)
                }
            } else {
                viewModelEventChannel.send(ViewModelEvent.NavigateToPermissionRequest)
            }
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateToHome : ViewModelEvent
        data object NavigateToPermissionRequest : ViewModelEvent
        data object ShowAlreadyAddedMessage : ViewModelEvent
    }
}
