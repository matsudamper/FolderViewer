package net.matsudamper.folderviewer.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.repository.StorageRepository

class SmbAddViewModel(
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    fun onSave(name: String, ip: String, username: String, password: String) {
        viewModelScope.launch {
            storageRepository.addSmbStorage(name, ip, username, password)
            _event.send(Event.SaveSuccess)
        }
    }

    sealed interface Event {
        data object SaveSuccess : Event
    }
}
