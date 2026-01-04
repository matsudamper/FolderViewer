package net.matsudamper.folderviewer.viewmodel.folder

import android.content.res.Resources
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
    private val resources: Resources,
) {
    @Suppress("LongMethod")
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

        val allImagePaths = allFiles.filter { !it.isDirectory && FileUtil.isImage(it.name) }
            .map { it.path }

        val uiItems = buildList {
            addUiItemsRecursive(
                folder = viewModelState.folder,
                isRoot = true,
                folderSortConfig = viewModelState.folderSortConfig,
                fileSortConfig = viewModelState.fileSortConfig,
                allImagePaths = allImagePaths,
            )

            if (viewModelState.currentPath.isEmpty() && viewModelState.favorites.isNotEmpty()) {
                add(FolderBrowserUiState.UiFileItem.Header(title = resources.getString(net.matsudamper.folderviewer.ui.R.string.favorites)))
                addAll(
                    viewModelState.favorites.map { favorite ->
                        FolderBrowserUiState.UiFileItem.File(
                            name = favorite.path,
                            path = favorite.path,
                            isDirectory = true,
                            size = 0,
                            lastModified = 0,
                            thumbnail = if (FileUtil.isImage(favorite.path)) {
                                FileImageSource.Thumbnail(storageId = storageId, path = favorite.path)
                            } else {
                                null
                            },
                            callbacks = object : FolderBrowserUiState.UiFileItem.File.Callbacks {
                                override fun onClick() {
                                    viewModelScope.launch {
                                        viewModelEventChannel.send(
                                            ViewModelEvent.NavigateToFolderBrowser(
                                                path = favorite.path,
                                                storageId = storageId,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }

        return FolderBrowserUiState(
            callbacks = callbacks,
            isLoading = viewModelState.isLoading,
            isRefreshing = viewModelState.isRefreshing,
            visibleFavoriteButton = viewModelState.currentPath.isNotEmpty(),
            currentPath = viewModelState.currentPath,
            title = viewModelState.currentPath.ifEmpty {
                viewModelState.storageName ?: viewModelState.currentPath
            },
            isFavorite = viewModelState.favoriteId != null,
            files = uiItems,
            folderSortConfig = viewModelState.folderSortConfig,
            fileSortConfig = viewModelState.fileSortConfig,
            displayConfig = viewModelState.displayConfig,
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
                    nameProvider = { it.name },
                ),
            ),
        )
        folder.folders.sortedWith(
            FileSortComparator(
                config = folderSortConfig,
                sizeProvider = { 0L },
                lastModifiedProvider = { 0L },
                nameProvider = { File(it.path).name },
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
        allImagePaths: List<String>,
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
                nameProvider = { File(it.path).name },
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
        private val allImagePaths: List<String>,
    ) : FolderBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() {
            viewModelScope.launch {
                val isImage = FileUtil.isImage(file.name)
                if (file.isDirectory) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToFolderBrowser(path = file.path, storageId = storageId),
                    )
                } else if (isImage) {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToImageViewer(
                            path = file.path,
                            storageId = storageId,
                            allPaths = allImagePaths,
                        ),
                    )
                }
            }
        }
    }
}
