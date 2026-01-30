package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class FileBrowserUiState(
    val visibleFolderBrowserButton: Boolean,
    val visibleFavoriteButton: Boolean,
    val isRefreshing: Boolean,
    val title: String,
    val isFavorite: Boolean,
    val sortConfig: FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val isSelectionMode: Boolean,
    val selectedCount: Int,
    val callbacks: Callbacks,
    val contentState: ContentState,
) {
    sealed interface ContentState {
        data object Loading : ContentState
        data object Error : ContentState
        data object Empty : ContentState
        data class Content(
            val files: List<UiFileItem>,
            val favorites: List<UiFileItem.File>,
        ) : ContentState
    }
    sealed interface UiFileItem {
        data class Header(
            val title: String,
        ) : UiFileItem

        @Immutable
        data class File(
            val name: String,
            val key: String,
            val isDirectory: Boolean,
            val size: Long,
            val lastModified: Long,
            val thumbnail: FileImageSource.Thumbnail?,
            val isSelected: Boolean,
            val callbacks: Callbacks,
        ) : UiFileItem {
            @Immutable
            interface Callbacks {
                fun onClick()
                fun onLongClick()
                fun onCheckedChange(checked: Boolean)
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
        fun onUploadFileClick()
        fun onUploadFolderClick()
        fun onCancelSelection()
    }
}
