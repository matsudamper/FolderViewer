package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.repository.FileItem

data class FileBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val files: List<FileItem>,
    val error: String?,
    val sortConfig: FileSortConfig,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBack()
        fun onFileClick(file: FileItem)
        fun onUpClick()
        fun onRefresh()
        fun onSortConfigChanged(config: FileSortConfig)
    }
}

data class FileSortConfig(
    val key: FileSortKey = FileSortKey.Name,
    val isAscending: Boolean = true,
)

enum class FileSortKey {
    Name,
    Date,
    Size,
}
