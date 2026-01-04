package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class FileBrowserUiState(
    val isLoading: Boolean,
    val visibleFolderBrowserButton: Boolean,
    val visibleFavoriteButton: Boolean,
    val isRefreshing: Boolean,
    val currentPath: String,
    val title: String,
    val isFavorite: Boolean,
    val files: List<UiFileItem>,
    val sortConfig: FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val callbacks: Callbacks,
) {
    sealed interface UiFileItem {
        data class Header(
            val title: String,
        ) : UiFileItem

        @Immutable
        data class File(
            val name: String,
            val path: String,
            val isDirectory: Boolean,
            val size: Long,
            val lastModified: Long,
            val thumbnail: FileImageSource.Thumbnail?,
            val callbacks: Callbacks,
        ) : UiFileItem {
            @Immutable
            fun interface Callbacks {
                fun onClick()
            }
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
        fun onSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(config: UiDisplayConfig)
        fun onFolderBrowserClick()
        fun onFavoriteClick()
    }
}
