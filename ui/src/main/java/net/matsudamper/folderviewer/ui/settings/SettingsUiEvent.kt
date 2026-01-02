package net.matsudamper.folderviewer.ui.settings

sealed interface SettingsUiEvent {
    data class ShowSnackbar(val message: String) : SettingsUiEvent
}
