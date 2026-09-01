package net.matsudamper.folderviewer.ui.browser

sealed interface FileBrowserUiEvent {
    data class ShowSnackbar(
        val message: String,
        val showAction: Boolean = false,
        val openExtractJobId: Long? = null,
    ) : FileBrowserUiEvent
    data object ShowCreateDirectoryDialog : FileBrowserUiEvent
    data object ShowCompressDialog : FileBrowserUiEvent
    data class ShowExtractDialog(
        val defaultName: String,
        val mode: ExtractDialogMode,
    ) : FileBrowserUiEvent
    data class ShowDeleteConfirmDialog(val count: Int) : FileBrowserUiEvent
}

enum class ExtractDialogMode {
    ZipFolder,
    ZstFile,
    XzFile,
}
