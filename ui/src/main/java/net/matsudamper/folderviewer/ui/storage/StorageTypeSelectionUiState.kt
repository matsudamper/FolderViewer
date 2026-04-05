package net.matsudamper.folderviewer.ui.storage

import androidx.compose.runtime.Immutable

data class StorageTypeSelectionUiState(
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onSmbClick()
        fun onLocalClick()
        fun onSharePointClick()
        fun onBack()
    }
}
