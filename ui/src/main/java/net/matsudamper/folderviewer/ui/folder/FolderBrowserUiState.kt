package net.matsudamper.folderviewer.ui.folder

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class FolderBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val title: String,
    val files: List<UiFileItem>,
    val sortConfig: FileSortConfig,
    val displayConfig: DisplayConfig,
    val callbacks: Callbacks,
) {
    data class UiFileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val thumbnail: FileImageSource.Thumbnail?,
        val callbacks: Callbacks,
    ) {
        @Immutable
        fun interface Callbacks {
            fun onClick()
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

    data class DisplayConfig(
        val displayMode: DisplayMode,
        val displaySize: DisplaySize,
    )

    enum class DisplayMode {
        List,
        Grid,
    }

    enum class DisplaySize {
        Small,
        Medium,
        Large,
    }

    @Immutable
    interface Callbacks {
        fun onRefresh()
        fun onBack()
        fun onSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(config: DisplayConfig)
        fun onFolderBrowserClick()
    }
}
