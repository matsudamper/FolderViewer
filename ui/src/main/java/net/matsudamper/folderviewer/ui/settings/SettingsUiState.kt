package net.matsudamper.folderviewer.ui.settings

import androidx.compose.runtime.Immutable

data class SettingsUiState(
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onClearDiskCache()
        fun onBack()
    }
}
