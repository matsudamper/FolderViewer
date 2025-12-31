package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState())

    val uiState: StateFlow<FileBrowserUiState> =
        MutableStateFlow(FileBrowserUiState()).also { mutableUiState ->
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

    private var fileRepository: FileRepository? = null

    init {
        loadFiles("")
    }

    private suspend fun getRepository(): FileRepository {
        val current = fileRepository
        if (current != null) return current

        val newRepo = storageRepository.getFileRepository(args.id)
            ?: throw IllegalStateException("Storage not found")
        fileRepository = newRepo
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

    fun onFileClick(file: FileItem) {
        if (file.isDirectory) {
            loadFiles(file.path)
        }
    }

    fun onBackClick() {
        val currentPath = viewModelStateFlow.value.currentPath
        if (currentPath.isEmpty()) {
            // 呼び出し元で画面を閉じる処理を行うため、ここでは何もしない
            return
        }

        val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath == currentPath) {
            // ルートの場合
            loadFiles("")
        } else {
            loadFiles(parentPath)
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
