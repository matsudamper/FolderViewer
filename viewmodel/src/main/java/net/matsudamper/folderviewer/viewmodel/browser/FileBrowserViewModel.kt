package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.repository.FavoriteConfiguration
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.PreferencesRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.viewmodel.FileUtil

@HiltViewModel(assistedFactory = FileBrowserViewModel.Companion.Factory::class)
class FileBrowserViewModel @AssistedInject constructor(
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
    application: Application,
    @Assisted private val arg: FileBrowser,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val uiChannelEvent = Channel<FileBrowserUiEvent>()
    val uiEvent: Flow<FileBrowserUiEvent> = uiChannelEvent.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(currentPath = arg.path.orEmpty()))

    private val callbacks: FileBrowserUiState.Callbacks = object : FileBrowserUiState.Callbacks {
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
            viewModelScope.launch {
                preferencesRepository.saveFileBrowserSortConfig(
                    PreferencesRepository.FileSortConfig(
                        key = when (config.key) {
                            FileBrowserUiState.FileSortKey.Name -> PreferencesRepository.FileSortKey.Name
                            FileBrowserUiState.FileSortKey.Date -> PreferencesRepository.FileSortKey.Date
                            FileBrowserUiState.FileSortKey.Size -> PreferencesRepository.FileSortKey.Size
                        },
                        isAscending = config.isAscending,
                    ),
                )
            }
        }

        override fun onDisplayModeChanged(config: UiDisplayConfig) {
            viewModelStateFlow.update { it.copy(displayConfig = config) }
            viewModelScope.launch {
                preferencesRepository.saveFileBrowserDisplayMode(
                    when (config.displayMode) {
                        UiDisplayConfig.DisplayMode.List -> PreferencesRepository.DisplayMode.List
                        UiDisplayConfig.DisplayMode.Grid -> PreferencesRepository.DisplayMode.Grid
                    },
                )
            }
        }

        override fun onFolderBrowserClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToFolderBrowser(
                        path = viewModelStateFlow.value.currentPath,
                        storageId = arg.storageId,
                    ),
                )
            }
        }

        override fun onFavoriteClick() {
            viewModelScope.launch {
                val state = viewModelStateFlow.value
                val favoriteId = state.favoriteId
                val currentPath = state.currentPath
                if (favoriteId != null) {
                    storageRepository.removeFavorite(favoriteId)
                    uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("Removed from favorites"))
                } else {
                    val name = if (currentPath.isEmpty()) {
                        state.storageName ?: "Storage"
                    } else {
                        currentPath.trim('/').split('/').lastOrNull() ?: currentPath
                    }

                    storageRepository.addFavorite(
                        storageId = arg.storageId,
                        path = currentPath,
                        name = name,
                    )
                    uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("Added to favorites"))
                }
            }
        }

        override fun onUploadFileClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.LaunchFilePicker)
            }
        }

        override fun onUploadFolderClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.LaunchFolderPicker)
            }
        }
    }

    val uiState: Flow<FileBrowserUiState> = channelFlow {
        viewModelStateFlow.collectLatest { viewModelState ->
            val sortedFiles = viewModelState.rawFiles.sortedWith(createComparator(viewModelState.sortConfig))
            val uiItems = sortedFiles.map { fileItem ->
                val isImage = FileUtil.isImage(fileItem.name)
                FileBrowserUiState.UiFileItem.File(
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
                    callbacks = FileItemCallbacks(fileItem, sortedFiles),
                )
            }

            val favoriteItems = viewModelState.favorites.map { favorite ->
                FileBrowserUiState.UiFileItem.File(
                    name = favorite.path,
                    path = favorite.path,
                    isDirectory = true,
                    size = 0,
                    lastModified = 0,
                    thumbnail = if (FileUtil.isImage(favorite.path)) {
                        FileImageSource.Thumbnail(
                            storageId = arg.storageId,
                            path = favorite.path,
                        )
                    } else {
                        null
                    },
                    callbacks = {
                        viewModelScope.launch {
                            viewModelEventChannel.send(
                                ViewModelEvent.NavigateToFileBrowser(
                                    path = favorite.path,
                                    storageId = arg.storageId,
                                ),
                            )
                        }
                    },
                )
            }

            val contentState = when {
                viewModelState.isLoading && uiItems.isEmpty() -> FileBrowserUiState.ContentState.Loading

                viewModelState.hasError && uiItems.isEmpty() -> FileBrowserUiState.ContentState.Error

                uiItems.isEmpty() -> FileBrowserUiState.ContentState.Empty

                else -> FileBrowserUiState.ContentState.Content(
                    files = uiItems,
                    favorites = favoriteItems,
                )
            }

            trySend(
                FileBrowserUiState(
                    callbacks = callbacks,
                    isRefreshing = viewModelState.isRefreshing,
                    currentPath = viewModelState.currentPath,
                    title = viewModelState.currentPath.ifEmpty {
                        viewModelState.storageName ?: viewModelState.currentPath
                    },
                    isFavorite = viewModelState.favoriteId != null,
                    visibleFavoriteButton = viewModelState.currentPath.isNotEmpty(),
                    sortConfig = viewModelState.sortConfig,
                    displayConfig = viewModelState.displayConfig,
                    visibleFolderBrowserButton = arg.path != null,
                    contentState = contentState,
                ),
            )
        }
    }

    private var fileRepository: FileRepository? = null

    init {
        loadFiles(arg.path.orEmpty())
        loadStorageName()
        viewModelScope.launch {
            loadSortConfig()
        }
        viewModelScope.launch {
            loadDisplayMode()
        }
        viewModelScope.launch {
            storageRepository.favorites
                .map { favorites ->
                    favorites.find { it.storageId == arg.storageId && it.path == (arg.path.orEmpty()) }?.id
                }
                .collectLatest { favoriteId ->
                    viewModelStateFlow.update { it.copy(favoriteId = favoriteId) }
                }
        }
        // Rootの時だけお気に入りを表示する
        if (arg.path == null) {
            viewModelScope.launch {
                storageRepository.favorites
                    .map { favorites ->
                        favorites.filter { it.storageId == arg.storageId }
                    }
                    .collectLatest { favorites ->
                        viewModelStateFlow.update { it.copy(favorites = favorites) }
                    }
            }
        }
    }

    private suspend fun loadSortConfig() {
        preferencesRepository.fileBrowserSortConfig.collect { config ->
            viewModelStateFlow.update {
                it.copy(
                    sortConfig = FileBrowserUiState.FileSortConfig(
                        key = when (config.key) {
                            PreferencesRepository.FileSortKey.Name -> FileBrowserUiState.FileSortKey.Name
                            PreferencesRepository.FileSortKey.Date -> FileBrowserUiState.FileSortKey.Date
                            PreferencesRepository.FileSortKey.Size -> FileBrowserUiState.FileSortKey.Size
                        },
                        isAscending = config.isAscending,
                    ),
                )
            }
        }
    }

    private suspend fun loadDisplayMode() {
        preferencesRepository.fileBrowserDisplayMode.collect { displayMode ->
            viewModelStateFlow.update {
                it.copy(
                    displayConfig = UiDisplayConfig(
                        displayMode = when (displayMode) {
                            PreferencesRepository.DisplayMode.List -> UiDisplayConfig.DisplayMode.List
                            PreferencesRepository.DisplayMode.Grid -> UiDisplayConfig.DisplayMode.Grid
                        },
                        displaySize = it.displayConfig.displaySize,
                    ),
                )
            }
        }
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
                    hasError = false,
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
                            hasError = true,
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
            val allPaths: List<String>,
        ) : ViewModelEvent

        data class NavigateToFolderBrowser(
            val path: String,
            val storageId: String,
        ) : ViewModelEvent

        data object LaunchFilePicker : ViewModelEvent
        data object LaunchFolderPicker : ViewModelEvent
    }

    suspend fun handleFileUpload(uri: android.net.Uri, fileName: String) {
        runCatching {
            val repository = getRepository()
            val currentPath = viewModelStateFlow.value.currentPath
            val contentResolver = getApplication<Application>().contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                repository.uploadFile(currentPath, fileName, inputStream)
            }
            fetchFilesInternal(currentPath)
            uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("ファイルをアップロードしました"))
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("アップロード失敗: ${e.message}"))
                }
            }
        }
    }

    suspend fun handleFolderUpload(uris: List<Pair<android.net.Uri, String>>) {
        runCatching {
            val repository = getRepository()
            val currentPath = viewModelStateFlow.value.currentPath
            val contentResolver = getApplication<Application>().contentResolver

            val folderName = "uploaded_folder_${System.currentTimeMillis()}"
            val filesToUpload = uris.mapNotNull { (uri, relativePath) ->
                contentResolver.openInputStream(uri)?.let { inputStream ->
                    net.matsudamper.folderviewer.repository.FileToUpload(
                        relativePath = relativePath,
                        inputStream = inputStream,
                    )
                }
            }

            repository.uploadFolder(currentPath, folderName, filesToUpload)
            fetchFilesInternal(currentPath)
            uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("フォルダをアップロードしました"))
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("アップロード失敗: ${e.message}"))
                }
            }
        }
    }

    private inner class FileItemCallbacks(
        private val fileItem: FileItem,
        private val sortedFiles: List<FileItem>,
    ) : FileBrowserUiState.UiFileItem.File.Callbacks {
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
                                path = fileItem.path,
                                storageId = arg.storageId,
                                allPaths = sortedFiles.filter { FileUtil.isImage(it.name) }.map { it.path },
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
        val displayConfig: UiDisplayConfig = UiDisplayConfig(
            displayMode = UiDisplayConfig.DisplayMode.List,
            displaySize = UiDisplayConfig.DisplaySize.Medium,
        ),
        val favoriteId: String? = null,
        val favorites: List<FavoriteConfiguration> = emptyList(),
        val hasError: Boolean = false,
    )

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: FileBrowser): FileBrowserViewModel
        }
    }
}
