package net.matsudamper.folderviewer.ui.browser

import android.net.Uri
import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class FileBrowserUiState(
    val visibleFolderBrowserButton: Boolean,
    val visibleOpenFolderWithExternalAppButton: Boolean,
    val visibleFavoriteButton: Boolean,
    val isRefreshing: Boolean,
    val title: String,
    val isFavorite: Boolean,
    val sortConfig: FileSortConfig,
    val displayConfig: UiDisplayConfig,
    val isSelectionMode: Boolean,
    val selectedCount: Int,
    val visibleCompressMenu: Boolean,
    val visibleRenameMenu: Boolean,
    val visibleExtractMenu: Boolean,
    val isPasteMode: Boolean,
    val extractDialog: ExtractDialogUiState?,
    val renameDialog: RenameDialog?,
    val callbacks: Callbacks,
    val contentState: ContentState,
) {
    @Immutable
    data class RenameDialog(
        val input: String,
        val confirmButtonEnabled: Boolean,
    )

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
            val subText: String,
            val thumbnail: FileImageSource.Thumbnail?,
            val isSelected: Boolean,
            val isLastOpened: Boolean,
            val enabled: Boolean,
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
        fun onUploadFileSelected(uri: Uri, fileName: String)
        fun onUploadFolderSelected(uri: Uri)
        fun onCreateDirectoryClick()
        fun onOpenFolderWithExternalAppClick()
        fun onConfirmCreateDirectory(directoryName: String)
        fun onCancelSelection()
        fun onSelectAllClick()
        fun onCopyClick()
        fun onCutClick()
        fun onShareClick()
        fun onOpenSelectedClick()
        fun onRenameClick()
        fun onRenameInputChange(input: String)
        fun onDismissRename()
        fun onConfirmRename()
        fun onCompressClick()
        fun onConfirmCompress(fileName: String)
        fun onExtractClick()
        fun onExtractPermissionResult()
        fun onOpenExtractResult(jobId: Long)
        fun onDeleteClick()
        fun onConfirmDelete()
        fun onPasteClick()
        fun onPastePermissionResult()
        fun onDeletePermissionResult()
        fun onCancelPaste()
    }
}
