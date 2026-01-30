package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.common.StorageId

data class HomeUiState(
    val storages: List<UiStorageConfiguration>,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onNavigateToSettings()
        fun onNavigateToUploadProgress()
        fun onAddStorageClick()
        fun onStorageClick(storage: UiStorageConfiguration)
        fun onEditStorageClick(storage: UiStorageConfiguration)
        fun onDeleteStorageClick(id: StorageId)
    }
}
