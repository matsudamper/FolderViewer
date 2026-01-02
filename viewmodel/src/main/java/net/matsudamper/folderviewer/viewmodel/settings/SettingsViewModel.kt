package net.matsudamper.folderviewer.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.settings.SettingsUiEvent

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiEvent = MutableSharedFlow<SettingsUiEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val uiEvent: SharedFlow<SettingsUiEvent> = _uiEvent.asSharedFlow()

    fun clearDiskCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CoilImageLoaderFactory.clearDiskCache(context)
                _uiEvent.emit(
                    SettingsUiEvent.ShowSnackbar(
                        context.getString(R.string.disk_cache_cleared),
                    ),
                )
            } catch (e: Exception) {
                _uiEvent.emit(
                    SettingsUiEvent.ShowSnackbar(
                        "${context.getString(R.string.disk_cache_clear_error)}: ${e.message}",
                    ),
                )
            }
        }
    }
}
