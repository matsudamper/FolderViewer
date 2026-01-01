package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.FileSortConfig
import net.matsudamper.folderviewer.ui.browser.FileSortKey
import net.matsudamper.folderviewer.ui.browser.UiFileItem
import net.matsudamper.folderviewer.viewmodel.FileUtil

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val arg: FileBrowser = savedStateHandle.toRoute<FileBrowser>()

    sealed interface Event {
        data object PopBackStack : Event
        data class NavigateToImageViewer(
            val path: String,
            val storageId: String,
        ) : Event
    }

    private val _event = Channel<Event>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val callbacks = object : FileBrowserUiState.Callbacks {
        override fun onBack() {
            viewModelScope.launch { _event.send(Event.PopBackStack) }
        }

        override fun onFileClick(file: UiFileItem) {
            if (file.isDirectory) {
                loadFiles(file.path)
            } else {
                val isImage = FileUtil.isImage(file.name.lowercase())

                if (isImage) {
                    viewModelScope.launch {
                        _event.send(
                            Event.NavigateToImageViewer(
                                file.path,
                                storageId = arg.storageId,
                            ),
                        )
                    }
                }
            }
        }

        override fun onUpClick() {
            val currentPath = viewModelStateFlow.value.currentPath
            if (currentPath.isEmpty()) {
                viewModelScope.launch { _event.send(Event.PopBackStack) }
            } else {
                val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
                if (parentPath == currentPath) {
                    loadFiles("")
                } else {
                    loadFiles(parentPath)
                }
            }
        }

        override fun onRefresh() {
            this@FileBrowserViewModel.onRefresh()
        }

        override fun onSortConfigChanged(config: FileSortConfig) {
            viewModelStateFlow.update { it.copy(sortConfig = config) }
        }

        override fun onErrorShown() {
            viewModelStateFlow.update { it.copy(error = null) }
        }
    }

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    val uiState: StateFlow<FileBrowserUiState> =
        MutableStateFlow(
            FileBrowserUiState(
                isLoading = false,
                isRefreshing = false,
                currentPath = "",
                files = emptyList(),
                error = null,
                sortConfig = FileSortConfig(),
                callbacks = callbacks,
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = viewModelState.isLoading,
                            isRefreshing = viewModelState.isRefreshing,
                            currentPath = viewModelState.currentPath,
                            files = viewModelState.rawFiles.sortedWith(createComparator(viewModelState.sortConfig))
                                .map { fileItem ->
                                    UiFileItem(
                                        name = fileItem.name,
                                        path = fileItem.path,
                                        isDirectory = fileItem.isDirectory,
                                        size = fileItem.size,
                                        lastModified = fileItem.lastModified,
                                    )
                                },
                            error = viewModelState.error,
                            sortConfig = viewModelState.sortConfig,
                        )
                    }
                }
            }
        }.asStateFlow()

    private val _fileRepository = MutableStateFlow<FileRepository?>(null)
    val fileRepository: StateFlow<FileRepository?> = _fileRepository.asStateFlow()

    init {
        loadFiles("")
    }

    private fun createComparator(config: FileSortConfig): Comparator<FileItem> {
        val comparator: Comparator<FileItem> = when (config.key) {
            FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FileSortKey.Date -> compareBy { it.lastModified }
            FileSortKey.Size -> compareBy { it.size }
        }

        val orderComparator = if (config.isAscending) comparator else comparator.reversed()

        return compareByDescending<FileItem> { it.isDirectory }.then(orderComparator)
    }

    private suspend fun getRepository(): FileRepository {
        val current = _fileRepository.value
        if (current != null) return current

        val newRepo = storageRepository.getFileRepository(arg.storageId)
            ?: throw IllegalStateException("Storage not found")
        _fileRepository.value = newRepo
        return newRepo
    }

    private fun loadFiles(path: String) {
        viewModelScope.launch {
            viewModelStateFlow.update { it.copy(isLoading = true, error = null) }
            fetchFilesInternal(path)
        }
    }

    fun onRefresh() {
        val path = viewModelStateFlow.value.currentPath
        viewModelScope.launch {
            viewModelStateFlow.update { it.copy(isRefreshing = true, error = null) }
            fetchFilesInternal(path)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchFilesInternal(path: String) {
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModelStateFlow.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val currentPath: String = "",
        val rawFiles: List<FileItem> = emptyList(),
        val sortConfig: FileSortConfig = FileSortConfig(),
        val error: String? = null,
    )
}
