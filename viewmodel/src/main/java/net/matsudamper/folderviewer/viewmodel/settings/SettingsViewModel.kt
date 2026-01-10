package net.matsudamper.folderviewer.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import net.matsudamper.folderviewer.coil.CoilManager
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.settings.SettingsUiEvent
import net.matsudamper.folderviewer.ui.settings.SettingsUiState

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coilManager: CoilManager,
) : ViewModel() {
    private val uiEventChannel = Channel<SettingsUiEvent>(Channel.UNLIMITED)
    val uiEventFlow: Flow<SettingsUiEvent> = uiEventChannel.receiveAsFlow()

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val callbacks: SettingsUiState.Callbacks = object : SettingsUiState.Callbacks {
        override fun onClearDiskCache() {
            clearDiskCache()
        }

        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = MutableStateFlow(
        SettingsUiState(callbacks = callbacks),
    ).asStateFlow()

    private fun clearDiskCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coilManager.clearMemoryCache()
                coilManager.clearDiskCache()
                uiEventChannel.trySend(
                    SettingsUiEvent.ShowSnackbar(
                        context.getString(R.string.disk_cache_cleared),
                    ),
                )
            } catch (e: Exception) {
                uiEventChannel.trySend(
                    SettingsUiEvent.ShowSnackbar(
                        "${context.getString(R.string.disk_cache_clear_error)}: ${e.message}",
                    ),
                )
            }
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
    }
}
