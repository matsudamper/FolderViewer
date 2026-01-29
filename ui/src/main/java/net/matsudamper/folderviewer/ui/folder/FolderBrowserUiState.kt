package net.matsudamper.folderviewer.ui.folder

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig

data class FolderBrowserUiState(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val visibleFavoriteButton: Boolean,
    val title: String,
    val isFavorite: Boolean,
    val files: List<UiFileItem>,
    val folderSortConfig: FileSortConfig,
    val fileSortConfig: FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val callbacks: Callbacks,
) {
    sealed interface UiFileItem {
        data class Header(
            val title: String,
        ) : UiFileItem

        data class File(
            val name: String,
            val key: String,
            val isDirectory: Boolean,
            val size: Long,
            val lastModified: Long,
            val thumbnail: FileImageSource.Thumbnail?,
            val callbacks: Callbacks,
        ) : UiFileItem {
            @Immutable
            interface Callbacks {
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
        fun onFolderSortConfigChanged(config: FileSortConfig)
        fun onFileSortConfigChanged(config: FileSortConfig)
        fun onDisplayModeChanged(config: UiDisplayConfig)
        fun onFavoriteClick()
        fun onCreateFolder(name: String)
    }
}
