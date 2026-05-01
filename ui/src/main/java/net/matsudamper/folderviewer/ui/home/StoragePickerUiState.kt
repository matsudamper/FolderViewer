package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Immutable

data class StoragePickerUiState(
    val storages: List<UiStorageConfiguration>,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onStorageClick(storage: UiStorageConfiguration)
    }
}
