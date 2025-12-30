package net.matsudamper.folderviewer.ui.browser

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

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<FileBrowser>()

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var fileRepository: FileRepository? = null

    init {
        loadFiles("")
    }

    private fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (fileRepository == null) {
                    fileRepository = storageRepository.getFileRepository(args.id)
                }

                val repository = fileRepository
                if (repository == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Storage not found") }
                    return@launch
                }

                val files = repository.getFiles(path)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = path,
                        files = files,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    fun onFileClick(file: FileItem) {
        if (file.isDirectory) {
            loadFiles(file.path)
        }
    }

    fun onBackClick() {
        val currentPath = _uiState.value.currentPath
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
        _uiState.update { it.copy(error = null) }
    }
}
