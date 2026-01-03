package net.matsudamper.folderviewer.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.settings.SettingsUiEvent

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) : ViewModel() {
    private val uiEventChannel = Channel<SettingsUiEvent>(Channel.UNLIMITED)
    val uiEventFlow: Flow<SettingsUiEvent> = uiEventChannel.receiveAsFlow()

    fun clearDiskCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CoilImageLoaderFactory.clearDiskCache(context, imageLoader)
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
}
