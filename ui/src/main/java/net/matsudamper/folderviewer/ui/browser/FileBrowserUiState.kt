package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

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

    @Immutable
    data class FileSortConfig(
        val key: FileSortKey,
        val isAscending: Boolean,
    )

    @Immutable
    enum class FileSortKey {
        Name,
        Date,
        Size,
    }

    @Immutable
    enum class DisplayMode {
        Small,
        Medium,
        Grid,
    }

    @Immutable
    interface Callbacks {
        fun onRefresh()
        fun onBack()
        fun onSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(mode: DisplayMode)
    }
}
