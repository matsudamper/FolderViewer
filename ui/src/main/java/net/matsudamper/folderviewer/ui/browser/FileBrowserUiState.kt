package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable

data class FileBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val title: String,
    val files: List<UiFileItem>,
    val sortConfig: FileSortConfig,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBack()
        fun onFileClick(file: UiFileItem)
        fun onUpClick()
        fun onRefresh()
        fun onSortConfigChanged(config: FileSortConfig)
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
