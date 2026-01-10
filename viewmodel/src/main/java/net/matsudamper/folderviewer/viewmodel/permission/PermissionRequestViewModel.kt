package net.matsudamper.folderviewer.viewmodel.permission

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.PermissionUtil
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.permission.PermissionRequestUiState

@HiltViewModel
class PermissionRequestViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    private val callbacks: PermissionRequestUiState.Callbacks = object : PermissionRequestUiState.Callbacks {
        override fun onGrantPermission() {
            onGrantPermissionInternal()
        }
    }

    val uiState: StateFlow<PermissionRequestUiState> =
        MutableStateFlow(
            PermissionRequestUiState(
                hasPermission = PermissionUtil.hasManageExternalStoragePermission(context),
                callbacks = callbacks,
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.value = PermissionRequestUiState(
                        hasPermission = viewModelState.hasPermission,
                        callbacks = callbacks,
                    )
                }
            }
        }.asStateFlow()

    private fun onGrantPermissionInternal() {
        viewModelScope.launch {
            viewModelEventChannel.send(ViewModelEvent.OpenSettings)
        }
    }

    fun checkPermission() {
        viewModelStateFlow.value = ViewModelState(
            hasPermission = PermissionUtil.hasManageExternalStoragePermission(context),
        )

        if (viewModelStateFlow.value.hasPermission) {
            viewModelScope.launch {
                storageRepository.detectLocalStorages()
                viewModelEventChannel.send(ViewModelEvent.PermissionGranted)
            }
        }
    }

    sealed interface ViewModelEvent {
        data object OpenSettings : ViewModelEvent
        data object PermissionGranted : ViewModelEvent
    }

    private data class ViewModelState(
        val hasPermission: Boolean = false,
    )
}
