package net.matsudamper.folderviewer.ui.folder

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig

data class FolderBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val title: String,
    val files: List<UiFileItem>,
    val folderSortConfig: FileSortConfig,
    val fileSortConfig: FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val callbacks: Callbacks,
) {
    sealed interface UiFileItem {
        data class Header(
            val path: String,
        ) : UiFileItem

        data class File(
            val name: String,
            val path: String,
            val isDirectory: Boolean,
            val size: Long,
            val lastModified: Long,
            val thumbnail: FileImageSource.Thumbnail?,
            val callbacks: Callbacks,
        ) : UiFileItem

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

    @Immutable
    interface Callbacks {
        fun onRefresh()
        fun onBack()
        fun onFolderSortConfigChanged(config: FileSortConfig)
        fun onFileSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(config: UiDisplayConfig)
    }
}
