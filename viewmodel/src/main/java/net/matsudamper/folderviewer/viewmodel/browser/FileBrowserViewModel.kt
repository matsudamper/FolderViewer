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

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<FileBrowser>()
    val storageId: String = args.id

    sealed interface Event {
        data object PopBackStack : Event
        data class NavigateToImageViewer(val path: String) : Event
    }

    private val _event = Channel<Event>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val callbacks = object : FileBrowserUiState.Callbacks {
        override val onBack: () -> Unit = {
            viewModelScope.launch { _event.send(Event.PopBackStack) }
        }
        override val onFileClick: (FileItem) -> Unit = { file ->
            if (file.isDirectory) {
                loadFiles(file.path)
            } else {
                val name = file.name.lowercase()
                val isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".bmp") ||
                    name.endsWith(".gif") || name.endsWith(".webp")

                if (isImage) {
                    viewModelScope.launch {
                        _event.send(Event.NavigateToImageViewer(file.path))
                    }
                }
            }
        }
        override val onUpClick: () -> Unit = {
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
        override val onRefresh: () -> Unit = {
            this@FileBrowserViewModel.onRefresh()
        }
    }

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    val uiState: StateFlow<FileBrowserUiState> =
        MutableStateFlow(FileBrowserUiState(callbacks = callbacks)).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = viewModelState.isLoading,
                            isRefreshing = viewModelState.isRefreshing,
                            currentPath = viewModelState.currentPath,
                            files = viewModelState.files,
                            error = viewModelState.error,
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

    private suspend fun getRepository(): FileRepository {
        val current = _fileRepository.value
        if (current != null) return current

        val newRepo = storageRepository.getFileRepository(args.id)
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
                    files = files,
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

    fun errorMessageShown() {
        viewModelStateFlow.update { it.copy(error = null) }
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val currentPath: String = "",
        val files: List<FileItem> = emptyList(),
        val error: String? = null,
    )
}
