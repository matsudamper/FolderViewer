package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.viewmodel.FileUtil

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val arg: FileBrowser = savedStateHandle.toRoute<FileBrowser>()

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val uiChannelEvent = Channel<FileBrowserUiEvent>()
    val uiEvent: Flow<FileBrowserUiEvent> = uiChannelEvent.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(currentPath = arg.path.orEmpty()))

    private val callbacks = object : FileBrowserUiState.Callbacks {
        override fun onRefresh() {
            val path = viewModelStateFlow.value.currentPath
            viewModelScope.launch {
                viewModelStateFlow.update { it.copy(isRefreshing = true) }
                fetchFilesInternal(path)
            }
        }

        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.PopBackStack)
            }
        }

        override fun onSortConfigChanged(config: FileBrowserUiState.FileSortConfig) {
            viewModelStateFlow.update { it.copy(sortConfig = config) }
        }

        override fun onDisplayModeChanged(config: FileBrowserUiState.DisplayConfig) {
            viewModelStateFlow.update { it.copy(displayConfig = config) }
        }
    }

    val uiState: Flow<FileBrowserUiState> = channelFlow {
        viewModelStateFlow.collectLatest { viewModelState ->
            trySend(
                FileBrowserUiState(
                    callbacks = callbacks,
                    isLoading = viewModelState.isLoading,
                    isRefreshing = viewModelState.isRefreshing,
                    currentPath = viewModelState.currentPath,
                    title = viewModelState.currentPath.ifEmpty {
                        viewModelState.storageName ?: viewModelState.currentPath
                    },
                    files = viewModelState.rawFiles.sortedWith(createComparator(viewModelState.sortConfig))
                        .map { fileItem ->
                            val isImage = FileUtil.isImage(fileItem.name.lowercase())
                            FileBrowserUiState.UiFileItem(
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
                                callbacks = FileItemCallbacks(fileItem),
                            )
                        },
                    sortConfig = viewModelState.sortConfig,
                    displayConfig = viewModelState.displayConfig,
                ),
            )
        }
    }

    private var fileRepository: FileRepository? = null

    init {
        loadFiles(arg.path.orEmpty())
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

    private fun createComparator(config: FileBrowserUiState.FileSortConfig): Comparator<FileItem> {
        val comparator: Comparator<FileItem> = when (config.key) {
            FileBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FileBrowserUiState.FileSortKey.Date -> compareBy { it.lastModified }
            FileBrowserUiState.FileSortKey.Size -> compareBy { it.size }
        }

        val orderComparator = if (config.isAscending) comparator else comparator.reversed()

        return compareByDescending<FileItem> { it.isDirectory }.then(orderComparator)
    }

    private suspend fun getRepository(): FileRepository {
        val current = fileRepository
        if (current != null) return current

        val newRepo = storageRepository.getFileRepository(arg.storageId)
            ?: throw IllegalStateException("Storage not found")
        fileRepository = newRepo
        return newRepo
    }

    private fun loadFiles(path: String) {
        viewModelScope.launch {
            viewModelStateFlow.update { it.copy(isLoading = true) }
            fetchFilesInternal(path)
        }
    }

    private suspend fun fetchFilesInternal(path: String) {
        runCatching {
            val repository = getRepository()
            val files = repository.getFiles(path)
            viewModelStateFlow.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    currentPath = path,
                    rawFiles = files,
                )
            }
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    viewModelStateFlow.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar(e.message ?: "Unknown error"))
                }
            }
        }
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
    }

    private inner class FileItemCallbacks(
        private val fileItem: FileItem,
    ) : FileBrowserUiState.UiFileItem.Callbacks {
        override fun onClick() {
            if (fileItem.isDirectory) {
                viewModelScope.launch {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToFileBrowser(
                            path = fileItem.path,
                            storageId = arg.storageId,
                        ),
                    )
                }
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

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val currentPath: String = "",
        val storageName: String? = null,
        val rawFiles: List<FileItem> = emptyList(),
        val sortConfig: FileBrowserUiState.FileSortConfig = FileBrowserUiState.FileSortConfig(
            key = FileBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        val displayConfig: FileBrowserUiState.DisplayConfig = FileBrowserUiState.DisplayConfig(
            displayMode = FileBrowserUiState.DisplayMode.List,
            displaySize = FileBrowserUiState.DisplaySize.Medium,
        ),
    )
}
