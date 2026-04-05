package net.matsudamper.folderviewer.ui.folder

import androidx.compose.runtime.Immutable

@Immutable
sealed interface FolderBrowserUiEvent {
    data class ShowSnackbar(val message: String) : FolderBrowserUiEvent
}
