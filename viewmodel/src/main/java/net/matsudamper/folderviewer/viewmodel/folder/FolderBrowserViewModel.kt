package net.matsudamper.folderviewer.viewmodel.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.FolderBrowser
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiEvent
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState
import net.matsudamper.folderviewer.viewmodel.FileUtil

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val arg: FolderBrowser = savedStateHandle.toRoute<FolderBrowser>()

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val uiChannelEvent = Channel<FolderBrowserUiEvent>()
    val uiEvent: Flow<FolderBrowserUiEvent> = uiChannelEvent.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(currentPath = arg.path.orEmpty()))

    private val callbacks = object : FolderBrowserUiState.Callbacks {
        override fun onRefresh() {
            refresh()
        }

        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.PopBackStack)
            }
        }

        override fun onFolderSortConfigChanged(config: FolderBrowserUiState.FileSortConfig) {
            viewModelStateFlow.update { it.copy(folderSortConfig = config) }
        }

        override fun onFileSortConfigChanged(config: FolderBrowserUiState.FileSortConfig) {
            viewModelStateFlow.update { it.copy(fileSortConfig = config) }
        }

        override fun onDisplayModeChanged(config: FolderBrowserUiState.DisplayConfig) {
            viewModelStateFlow.update { it.copy(displayConfig = config) }
        }
    }

    val uiState: StateFlow<FolderBrowserUiState> = combine(
        viewModelStateFlow,
    ) { (viewModelState) ->
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

        val rootPath = arg.path.orEmpty()
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
                                    storageId = arg.storageId,
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

        FolderBrowserUiState(
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FolderBrowserUiState(
            callbacks = callbacks,
            isLoading = true,
            isRefreshing = false,
            currentPath = arg.path.orEmpty(),
            title = arg.path.orEmpty(),
            files = emptyList(),
            folderSortConfig = viewModelStateFlow.value.folderSortConfig,
            fileSortConfig = viewModelStateFlow.value.fileSortConfig,
            displayConfig = viewModelStateFlow.value.displayConfig,
        ),
    )

    private var fileRepository: FileRepository? = null
    private var fetchJob: Job? = null

    init {
        refresh()
        loadStorageName()
    }

    private fun loadStorageName() {
        viewModelScope.launch {
            val storage = storageRepository.storageList.first().find { it.id == arg.storageId }
            if (storage != null) {
                viewModelStateFlow.update { it.copy(storageName = storage.name) }
            }
        }
    }

    private fun createFileItemComparator(config: FolderBrowserUiState.FileSortConfig): Comparator<FileItem> {
        val comparator: Comparator<FileItem> = when (config.key) {
            FolderBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FolderBrowserUiState.FileSortKey.Date -> compareBy { it.lastModified }
            FolderBrowserUiState.FileSortKey.Size -> compareBy { it.size }
        }

        return if (config.isAscending) comparator else comparator.reversed()
    }

    private fun createInternalFileItemComparator(config: FolderBrowserUiState.FileSortConfig): Comparator<InternalFileItem> {
        val comparator: Comparator<InternalFileItem> = when (config.key) {
            FolderBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileItem.name }
            FolderBrowserUiState.FileSortKey.Date -> compareBy { it.fileItem.lastModified }
            FolderBrowserUiState.FileSortKey.Size -> compareBy { it.fileItem.size }
        }

        return if (config.isAscending) comparator else comparator.reversed()
    }

    private suspend fun getRepository(): FileRepository {
        val current = fileRepository
        if (current != null) return current

        val newRepo = storageRepository.getFileRepository(arg.storageId)
            ?: throw IllegalStateException("Storage not found")
        fileRepository = newRepo
        return newRepo
    }

    private fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            viewModelStateFlow.update {
                it.copy(
                    isLoading = it.rawFiles.isEmpty(),
                    isRefreshing = it.rawFiles.isNotEmpty(),
                    rawFiles = emptyList(),
                )
            }
            try {
                val repository = getRepository()
                fetchAllFilesRecursive(repository, arg.path.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiChannelEvent.trySend(FolderBrowserUiEvent.ShowSnackbar(e.message ?: "Unknown error"))
            } finally {
                viewModelStateFlow.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    private suspend fun fetchAllFilesRecursive(repository: FileRepository, path: String) {
        val files = repository.getFiles(path)
        val itemsWithParent = files.map { it.toInternal(path) }

        viewModelStateFlow.update { state ->
            state.copy(rawFiles = state.rawFiles + itemsWithParent)
        }

        // 深さ優先で再帰的に取得
        for (file in itemsWithParent) {
            if (file.fileItem.isDirectory) {
                fetchAllFilesRecursive(repository, file.fileItem.path)
            }
        }
    }

    private fun FileItem.toInternal(parentPath: String): InternalFileItem {
        return InternalFileItem(
            fileItem = this,
            parentPath = parentPath,
        )
    }

    sealed interface ViewModelEvent {
        data object PopBackStack : ViewModelEvent
        data class NavigateToFileBrowser(
            val path: String,
            val storageId: String,
        ) : ViewModelEvent

        data class NavigateToImageViewer(
            val path: String,
            val storageId: String,
        ) : ViewModelEvent

        data class NavigateToFolderBrowser(
            val path: String,
            val storageId: String,
        ) : ViewModelEvent
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
                                fileItem.path,
                                storageId = arg.storageId,
                            ),
                        )
                    }
                }
            }
        }
    }

    private data class InternalFileItem(
        val fileItem: FileItem,
        val parentPath: String,
    )

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val currentPath: String = "",
        val storageName: String? = null,
        val rawFiles: List<InternalFileItem> = emptyList(),
        val folderSortConfig: FolderBrowserUiState.FileSortConfig = FolderBrowserUiState.FileSortConfig(
            key = FolderBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        val fileSortConfig: FolderBrowserUiState.FileSortConfig = FolderBrowserUiState.FileSortConfig(
            key = FolderBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        val displayConfig: FolderBrowserUiState.DisplayConfig = FolderBrowserUiState.DisplayConfig(
            displayMode = FolderBrowserUiState.DisplayMode.List,
            displaySize = FolderBrowserUiState.DisplaySize.Medium,
        ),
    )
}
