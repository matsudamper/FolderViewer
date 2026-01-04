package net.matsudamper.folderviewer.viewmodel.folder

import kotlin.collections.orEmpty
import kotlin.text.CASE_INSENSITIVE_ORDER
import kotlin.text.compareTo
import kotlin.text.ifEmpty
import kotlin.text.lowercase
import kotlin.text.removePrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState
import net.matsudamper.folderviewer.viewmodel.FileUtil
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel.InternalFileItem
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
        val groups = viewModelState.rawFiles
            .groupBy { it.parentPath }

        // フォルダ（ヘッダー）のソート用情報を取得
        val folderInfos = viewModelState.rawFiles
            .filter { it.fileItem.isDirectory }
            .associateBy { it.fileItem.path }

        val sortedParentPaths = groups.keys.sortedWith { path1, path2 ->
            val info1 = folderInfos[path1]
            val info2 = folderInfos[path2]

            if (info1 != null && info2 != null) {
                createFileItemComparator(viewModelState.folderSortConfig).compare(info1.fileItem, info2.fileItem)
            } else {
                path1.compareTo(path2, ignoreCase = true)
            }
        }

        val rootPath = path.orEmpty()
        val sortedFiles = sortedParentPaths.flatMap { parentPath ->
            val filesInFolder = groups[parentPath].orEmpty()
            val filesOnly = filesInFolder.filter { !it.fileItem.isDirectory }

            if (filesOnly.isEmpty()) {
                emptyList()
            } else {
                val relativePath = if (parentPath == rootPath) {
                    "."
                } else {
                    parentPath.removePrefix(rootPath).removePrefix("/")
                }
                val header = FolderBrowserUiState.UiFileItem.Header(
                    path = relativePath,
                )
                val items = filesOnly
                    .sortedWith(createInternalFileItemComparator(viewModelState.fileSortConfig))
                    .map { internalItem ->
                        val fileItem = internalItem.fileItem
                        val isImage = FileUtil.isImage(fileItem.name)
                        FolderBrowserUiState.UiFileItem.File(
                            name = fileItem.name,
                            path = fileItem.path,
                            isDirectory = fileItem.isDirectory,
                            size = fileItem.size,
                            lastModified = fileItem.lastModified,
                            thumbnail = if (isImage) {
                                FileImageSource.Thumbnail(
                                    storageId = storageId,
                                    path = fileItem.path,
                                )
                            } else {
                                null
                            },
                            callbacks = FileItemCallbacks(internalItem),
                        )
                    }
                listOf(header) + items
            }
        }

        return FolderBrowserUiState(
            callbacks = callbacks,
            isLoading = viewModelState.isLoading,
            isRefreshing = viewModelState.isRefreshing,
            currentPath = viewModelState.currentPath,
            title = viewModelState.currentPath.ifEmpty {
                viewModelState.storageName ?: viewModelState.currentPath
            },
            files = sortedFiles,
            folderSortConfig = viewModelState.folderSortConfig,
            fileSortConfig = viewModelState.fileSortConfig,
            displayConfig = viewModelState.displayConfig,
        )
    }

    private fun createFileItemComparator(config: FolderBrowserUiState.FileSortConfig): Comparator<FileItem> {
        val comparator: Comparator<FileItem> = when (config.key) {
            FolderBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FolderBrowserUiState.FileSortKey.Date -> compareBy { it.lastModified }
            FolderBrowserUiState.FileSortKey.Size -> compareBy { it.size }
        }

        return if (config.isAscending) comparator else comparator.reversed()
    }

    private fun createInternalFileItemComparator(
        config: FolderBrowserUiState.FileSortConfig,
    ): Comparator<InternalFileItem> {
        val comparator: Comparator<InternalFileItem> = when (config.key) {
            FolderBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileItem.name }
            FolderBrowserUiState.FileSortKey.Date -> compareBy { it.fileItem.lastModified }
            FolderBrowserUiState.FileSortKey.Size -> compareBy { it.fileItem.size }
        }

        return if (config.isAscending) comparator else comparator.reversed()
    }

    private inner class FileItemCallbacks(
        private val internalItem: InternalFileItem,
    ) : FolderBrowserUiState.UiFileItem.Callbacks {
        override fun onClick() {
            val fileItem = internalItem.fileItem
            if (fileItem.isDirectory) {
                // Folder browser recursive view
            } else {
                val isImage = FileUtil.isImage(fileItem.name.lowercase())

                if (isImage) {
                    viewModelScope.launch {
                        viewModelEventChannel.send(
                            ViewModelEvent.NavigateToImageViewer(
                                path = fileItem.path,
                                storageId = storageId,
                            ),
                        )
                    }
                }
            }
        }
    }
}