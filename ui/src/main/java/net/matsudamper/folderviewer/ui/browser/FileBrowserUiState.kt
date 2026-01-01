package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable

public data class FileBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val files: List<UiFileItem>,
    val error: String?,
    val sortConfig: FileSortConfig,
    val callbacks: Callbacks,
) {
    @Immutable
    public interface Callbacks {
        public fun onBack()
        public fun onFileClick(file: UiFileItem)
        public fun onUpClick()
        public fun onRefresh()
        public fun onSortConfigChanged(config: FileSortConfig)
        public fun onErrorShown()
    }
}

public data class FileSortConfig(
    val key: FileSortKey = FileSortKey.Name,
    val isAscending: Boolean = true,
)

public enum class FileSortKey {
    Name,
    Date,
    Size,
}
