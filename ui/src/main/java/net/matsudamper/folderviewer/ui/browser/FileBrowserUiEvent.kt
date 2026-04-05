package net.matsudamper.folderviewer.ui.browser

sealed interface FileBrowserUiEvent {
    data class ShowSnackbar(val message: String) : FileBrowserUiEvent
    data object ShowCreateDirectoryDialog : FileBrowserUiEvent
}
