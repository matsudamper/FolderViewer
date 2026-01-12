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
import net.matsudamper.folderviewer.common.FileObjectId
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
    private val fileObjectId = arg.fileId

    private val displayName get() = arg.displayPath ?: viewModelStateFlow.value.storageName ?: "null"
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private val callbacks: FileBrowserUiState.Callbacks = object : FileBrowserUiState.Callbacks {
        override fun onRefresh() {
            viewModelScope.launch {
                viewModelStateFlow.update { it.copy(isRefreshing = true) }
                fetchFilesInternal()
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
                        id = fileObjectId,
                        storageId = arg.storageId,
                        displayPath = arg.displayPath,
                    ),
                )
            }
        }

        override fun onFavoriteClick() {
            val path = when (fileObjectId) {
                is FileObjectId.Root -> ""
                is FileObjectId.Item -> fileObjectId.id
            }
            viewModelScope.launch {
                val state = viewModelStateFlow.value
                val favoriteId = state.favoriteId
                if (favoriteId != null) {
                    storageRepository.removeFavorite(favoriteId)
                    uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("Removed from favorites"))
                } else {
                    val displayPath = arg.displayPath
                    val name = if (displayPath == null) {
                        state.storageName ?: "Storage"
                    } else {
                        displayPath.trim('/').split('/').lastOrNull()
                            ?: viewModelStateFlow.value.storageName
                            ?: "null"
                    }

                    storageRepository.addFavorite(
                        storageId = arg.storageId,
                        path = path,
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
                val isImage = FileUtil.isImage(fileItem.displayPath)
                FileBrowserUiState.UiFileItem.File(
                    name = fileItem.displayPath,
                    path = fileItem.id,
                    isDirectory = fileItem.isDirectory,
                    size = fileItem.size,
                    lastModified = fileItem.lastModified,
                    thumbnail = if (isImage) {
                        FileImageSource.Thumbnail(
                            storageId = arg.storageId,
                            path = fileItem.id,
                        )
                    } else {
                        null
                    },
                    callbacks = FileItemCallbacks(fileItem, sortedFiles),
                )
            }
            trySend(
                FileBrowserUiState(
                    callbacks = callbacks,
                    isLoading = viewModelState.isLoading,
                    isRefreshing = viewModelState.isRefreshing,
                    title = arg.displayPath ?: viewModelState.storageName ?: "null",
                    isFavorite = viewModelState.favoriteId != null,
                    visibleFavoriteButton = arg.displayPath != null,
                    files = uiItems,
                    sortConfig = viewModelState.sortConfig,
                    displayConfig = viewModelState.displayConfig,
                    visibleFolderBrowserButton = arg.displayPath != null,
                    favorites = viewModelState.favorites.map { favorite ->
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
                                            displayPath = favorite.path,
                                            storageId = arg.storageId,
                                            id = favorite.path,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                ),
            )
        }
    }

    private var fileRepository: FileRepository? = null

    init {
        loadFiles()
        loadStorageName()
        viewModelScope.launch {
            loadSortConfig()
        }
        viewModelScope.launch {
            loadDisplayMode()
        }
        viewModelScope.launch {
            val path = when (fileObjectId) {
                is FileObjectId.Root -> return@launch
                is FileObjectId.Item -> fileObjectId.id
            }
            storageRepository.favorites
                .map { favorites ->
                    favorites.find { it.storageId == arg.storageId && it.path == path }?.id
                }
                .collectLatest { favoriteId ->
                    viewModelStateFlow.update { it.copy(favoriteId = favoriteId) }
                }
        }
        // Rootの時だけお気に入りを表示する
        if (arg.displayPath == null) {
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
            FileBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayPath }
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

    private fun loadFiles() {
        viewModelScope.launch {
            viewModelStateFlow.update { it.copy(isLoading = true) }
            fetchFilesInternal()
        }
    }

    private suspend fun fetchFilesInternal() {
        runCatching {
            val repository = getRepository()
            val files = repository.getFiles(fileObjectId)
            viewModelStateFlow.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    rawFiles = files,
                )
            }
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
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
            val displayPath: String,
            val storageId: String,
            val id: String,
        ) : ViewModelEvent

        data class NavigateToImageViewer(
            val path: String,
            val storageId: String,
            val allPaths: List<String>,
        ) : ViewModelEvent

        data class NavigateToFolderBrowser(
            val id: FileObjectId,
            val displayPath: String?,
            val storageId: String,
        ) : ViewModelEvent

        data object LaunchFilePicker : ViewModelEvent
        data object LaunchFolderPicker : ViewModelEvent
    }

    suspend fun handleFileUpload(uri: android.net.Uri, fileName: String) {
        runCatching {
            val repository = getRepository()
            val contentResolver = getApplication<Application>().contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                repository.uploadFile(fileObjectId, fileName, inputStream)
            }
            fetchFilesInternal()
            uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("ファイルをアップロードしました"))
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("アップロード失敗: ${e.message}"))
                }
            }
        }
    }

    suspend fun handleFolderUpload(uris: List<Pair<android.net.Uri, String>>) {
        runCatching {
            val repository = getRepository()
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

            repository.uploadFolder(fileObjectId, folderName, filesToUpload)
            fetchFilesInternal()
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
                            displayPath = "$displayName/${fileItem.displayPath}",
                            storageId = arg.storageId,
                            id = fileItem.id,
                        ),
                    )
                }
            } else {
                val isImage = FileUtil.isImage(fileItem.displayPath.lowercase())

                if (isImage) {
                    viewModelScope.launch {
                        viewModelEventChannel.send(
                            ViewModelEvent.NavigateToImageViewer(
                                path = fileItem.id,
                                storageId = arg.storageId,
                                allPaths = sortedFiles.filter { FileUtil.isImage(it.displayPath) }.map { it.id },
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
    )

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: FileBrowser): FileBrowserViewModel
        }
    }
}
