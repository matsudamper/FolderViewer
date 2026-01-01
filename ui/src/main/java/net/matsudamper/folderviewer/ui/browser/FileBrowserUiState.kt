package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.repository.FileItem

data class FileBrowserUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val error: String? = null,
    val sortConfig: FileSortConfig = FileSortConfig(),
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        val onBack: () -> Unit
        val onFileClick: (FileItem) -> Unit
        val onUpClick: () -> Unit
        val onRefresh: () -> Unit
        val onSortConfigChanged: (FileSortConfig) -> Unit
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
