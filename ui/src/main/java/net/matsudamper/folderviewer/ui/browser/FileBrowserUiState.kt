package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable

data class FileBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val title: String,
    val files: List<UiFileItem>,
    val sortConfig: FileSortConfig,
    val displayMode: DisplayMode,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBack()
        fun onFileClick(file: UiFileItem)
        fun onUpClick()
        fun onRefresh()
        fun onSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(mode: DisplayMode)
    }
}

data class FileSortConfig(
    val key: FileSortKey,
    val isAscending: Boolean,
)

enum class FileSortKey {
    Name,
    Date,
    Size,
}

enum class DisplayMode {
    Small,
    Medium,
    Grid,
}
