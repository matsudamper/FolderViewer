package net.matsudamper.folderviewer.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _event = MutableSharedFlow<Event>()
    val event: SharedFlow<Event> = _event.asSharedFlow()

    fun clearDiskCache() {
        viewModelScope.launch(Dispatchers.IO) {
            CoilImageLoaderFactory.clearDiskCache(context)
            _event.emit(Event.CacheClearSuccess)
        }
    }

    sealed interface Event {
        data object CacheClearSuccess : Event
    }
}
