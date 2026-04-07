package net.matsudamper.folderviewer.ui.browser

sealed interface FileBrowserUiEvent {
    data class ShowSnackbar(val message: String) : FileBrowserUiEvent
    data object ShowCreateDirectoryDialog : FileBrowserUiEvent
    data class ShowDeleteConfirmDialog(val count: Int) : FileBrowserUiEvent
}
