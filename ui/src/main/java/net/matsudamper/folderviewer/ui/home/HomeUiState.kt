package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Immutable

data class HomeUiState(
    val storages: List<UiStorageConfiguration>,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onNavigateToSettings()
        fun onAddStorageClick()
        fun onStorageClick(storage: UiStorageConfiguration)
        fun onEditStorageClick(storage: UiStorageConfiguration)
        fun onDeleteStorageClick(id: String)
    }
}
