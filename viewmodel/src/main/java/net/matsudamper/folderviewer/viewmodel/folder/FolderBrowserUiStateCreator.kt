package net.matsudamper.folderviewer.viewmodel.folder

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState
import net.matsudamper.folderviewer.viewmodel.FileSortComparator
import net.matsudamper.folderviewer.viewmodel.FileUtil
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel.ViewModelEvent

class FolderBrowserUiStateCreator(
    private val callbacks: FolderBrowserUiState.Callbacks,
    private val viewModelScope: CoroutineScope,
    private val path: String?,
    private val storageId: String,
    private val viewModelEventChannel: Channel<ViewModelEvent>,
) {
    fun create(
        viewModelState: FolderBrowserViewModel.ViewModelState,
    ): FolderBrowserUiState {
        return FolderBrowserUiState(
            callbacks = callbacks,
            isLoading = viewModelState.isLoading,
            isRefreshing = viewModelState.isRefreshing,
            currentPath = viewModelState.currentPath,
            title = viewModelState.currentPath.ifEmpty {
                viewModelState.storageName ?: viewModelState.currentPath
            },
            files = buildList {
                addItemsRecursive(
                    folder = viewModelState.folder,
                    isRoot = true,
                    folderSortConfig = viewModelState.folderSortConfig,
                    fileSortConfig = viewModelState.fileSortConfig,
                )
            },
            folderSortConfig = viewModelState.folderSortConfig,
            fileSortConfig = viewModelState.fileSortConfig,
            displayConfig = viewModelState.displayConfig,
        )
    }

    private fun MutableList<FolderBrowserUiState.UiFileItem>.addItemsRecursive(
        folder: FolderBrowserViewModel.ViewModelState.Folder,
        isRoot: Boolean,
        folderSortConfig: FolderBrowserUiState.FileSortConfig,
        fileSortConfig: FolderBrowserUiState.FileSortConfig,
    ) {
        if (!isRoot) {
            val titleText = if (path.isNullOrEmpty()) {
                folder.path
            } else {
                folder.path.removePrefix(path).removePrefix(File.separator)
            }
            add(FolderBrowserUiState.UiFileItem.Header(title = titleText))
        }

        folder.files.sortedWith(
            FileSortComparator(
                config = fileSortConfig,
                sizeProvider = { it.size },
                lastModifiedProvider = { it.lastModified },
                nameProvider = { it.name },
            ),
        ).forEach { file ->
            val isImage = FileUtil.isImage(file.name)
            add(
                FolderBrowserUiState.UiFileItem.File(
                    name = file.name,
                    path = file.path,
                    isDirectory = file.isDirectory,
                    size = file.size,
                    lastModified = file.lastModified,
                    thumbnail = if (isImage) {
                        FileImageSource.Thumbnail(storageId = storageId, path = file.path)
                    } else {
                        null
                    },
                    callbacks = FileItemCallbacks(file = file),
                ),
            )
        }
        folder.folders.sortedWith(
            FileSortComparator(
                config = folderSortConfig,
                sizeProvider = { 0L },
                lastModifiedProvider = { 0L },
                nameProvider = { File(it.path).name },
            ),
        ).forEach { childFolder ->
            addItemsRecursive(
                folder = childFolder,
                isRoot = false,
                folderSortConfig = folderSortConfig,
                fileSortConfig = fileSortConfig,
            )
        }
    }

    private inner class FileItemCallbacks(
        private val file: FileItem,
    ) : FolderBrowserUiState.UiFileItem.Callbacks {
        override fun onClick() {
            viewModelScope.launch {
                val isImage = FileUtil.isImage(file.name)
                if (file.isDirectory) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToFolderBrowser(path = file.path, storageId = storageId),
                    )
                } else if (isImage) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToImageViewer(path = file.path, storageId = storageId),
                    )
                }
            }
        }
    }
}
