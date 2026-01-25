package net.matsudamper.folderviewer.viewmodel.folder

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel.ViewModelEvent
import net.matsudamper.folderviewer.viewmodel.util.FileSortComparator
import net.matsudamper.folderviewer.viewmodel.util.FileUtil

class FolderBrowserUiStateCreator(
    private val callbacks: FolderBrowserUiState.Callbacks,
    private val viewModelScope: CoroutineScope,
    private val fileObjectId: FileObjectId,
    private val storageId: StorageId,
    private val viewModelEventChannel: Channel<ViewModelEvent>,
    private val displayPath: String?,
) {
    fun create(
        viewModelState: FolderBrowserViewModel.ViewModelState,
    ): FolderBrowserUiState {
        val allFiles = buildList {
            addFoldersRecursive(
                folder = viewModelState.folder,
                folderSortConfig = viewModelState.folderSortConfig,
                fileSortConfig = viewModelState.fileSortConfig,
            )
        }

        val allImagePaths = allFiles.filter { !it.isDirectory && FileUtil.isImage(it.displayPath) }
            .map { it.id }

        val uiItems = buildList {
            addUiItemsRecursive(
                folder = viewModelState.folder,
                isRoot = true,
                folderSortConfig = viewModelState.folderSortConfig,
                fileSortConfig = viewModelState.fileSortConfig,
                allImagePaths = allImagePaths,
            )
        }

        return FolderBrowserUiState(
            callbacks = callbacks,
            isLoading = viewModelState.isLoading,
            isRefreshing = viewModelState.isRefreshing,
            title = displayPath ?: viewModelState.storageName ?: "null",
            files = uiItems,
            folderSortConfig = viewModelState.folderSortConfig,
            fileSortConfig = viewModelState.fileSortConfig,
            displayConfig = viewModelState.displayConfig,
            isFavorite = viewModelState.favoriteId != null,
            visibleFavoriteButton = fileObjectId !is FileObjectId.Root,
        )
    }

    private fun MutableList<FileItem>.addFoldersRecursive(
        folder: FolderBrowserViewModel.ViewModelState.Folder,
        folderSortConfig: FolderBrowserUiState.FileSortConfig,
        fileSortConfig: FolderBrowserUiState.FileSortConfig,
    ) {
        addAll(
            folder.files.sortedWith(
                FileSortComparator(
                    config = fileSortConfig,
                    sizeProvider = { it.size },
                    lastModifiedProvider = { it.lastModified },
                    nameProvider = { it.displayPath },
                ),
            ),
        )
        folder.folders.sortedWith(
            FileSortComparator(
                config = folderSortConfig,
                sizeProvider = { 0L },
                lastModifiedProvider = { 0L },
                nameProvider = { File(it.displayPath.orEmpty()).name },
            ),
        ).forEach { childFolder ->
            addFoldersRecursive(
                folder = childFolder,
                folderSortConfig = folderSortConfig,
                fileSortConfig = fileSortConfig,
            )
        }
    }

    private fun MutableList<FolderBrowserUiState.UiFileItem>.addUiItemsRecursive(
        folder: FolderBrowserViewModel.ViewModelState.Folder,
        isRoot: Boolean,
        folderSortConfig: FolderBrowserUiState.FileSortConfig,
        fileSortConfig: FolderBrowserUiState.FileSortConfig,
        allImagePaths: List<FileObjectId.Item>,
    ) {
        if (!isRoot) {
            val folderPath = folder.displayPath
            val titleText = if (folderPath.isNullOrEmpty()) {
                folderPath ?: "null"
            } else {
                folderPath.removePrefix(displayPath.orEmpty()).removePrefix(File.separator)
            }
            add(FolderBrowserUiState.UiFileItem.Header(title = titleText))
        }

        folder.files.sortedWith(
            FileSortComparator(
                config = fileSortConfig,
                sizeProvider = { it.size },
                lastModifiedProvider = { it.lastModified },
                nameProvider = { it.displayPath },
            ),
        ).forEach { file ->
            val isImage = FileUtil.isImage(file.displayPath)
            add(
                FolderBrowserUiState.UiFileItem.File(
                    name = file.displayPath,
                    key = file.id.id,
                    isDirectory = file.isDirectory,
                    size = file.size,
                    lastModified = file.lastModified,
                    thumbnail = if (isImage) {
                        FileImageSource.Thumbnail(storageId = storageId, fileId = file.id)
                    } else {
                        null
                    },
                    callbacks = FileItemCallbacks(
                        file = file,
                        allImagePaths = allImagePaths,
                    ),
                ),
            )
        }
        folder.folders.sortedWith(
            FileSortComparator(
                config = folderSortConfig,
                sizeProvider = { 0L },
                lastModifiedProvider = { 0L },
                nameProvider = { File(it.displayPath.orEmpty()).name },
            ),
        ).forEach { childFolder ->
            addUiItemsRecursive(
                folder = childFolder,
                isRoot = false,
                folderSortConfig = folderSortConfig,
                fileSortConfig = fileSortConfig,
                allImagePaths = allImagePaths,
            )
        }
    }

    private inner class FileItemCallbacks(
        private val file: FileItem,
        private val allImagePaths: List<FileObjectId.Item>,
    ) : FolderBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() {
            viewModelScope.launch {
                val isImage = FileUtil.isImage(file.displayPath)
                if (file.isDirectory) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToFolderBrowser(
                            fileId = file.id,
                            storageId = storageId,
                            displayPath = file.displayPath,
                        ),
                    )
                } else if (isImage) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToImageViewer(
                            fileId = file.id,
                            storageId = storageId,
                            allPaths = allImagePaths,
                        ),
                    )
                }
            }
        }
    }
}
