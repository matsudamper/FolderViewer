package net.matsudamper.folderviewer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltViewModel
class HomeViewModel @Inject constructor(
    storageRepository: StorageRepository,
) : ViewModel() {
    val storages: StateFlow<List<StorageConfiguration>> = storageRepository.storageList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
