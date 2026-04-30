package net.matsudamper.folderviewer.ui.picker

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig

data class ExternalFilePickerUiState(
    val title: String,
    val isRefreshing: Boolean,
    val isMultipleMode: Boolean,
    val selectedCount: Int,
    val sortConfig: FileBrowserUiState.FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val contentState: ContentState,
    val callbacks: Callbacks,
) {
    sealed interface ContentState {
        data object Loading : ContentState
        data object Error : ContentState
        data object Empty : ContentState
        data class Content(
            val files: List<FileBrowserUiState.UiFileItem>,
        ) : ContentState
    }

    @Immutable
    interface Callbacks {
        fun onBack()
        fun onRefresh()
        fun onSortConfigChanged(config: FileBrowserUiState.FileSortConfig)
        fun onDisplayModeChanged(config: UiDisplayConfig)
        fun onSelectedCountClick()
        fun onSubmit()
    }
}
