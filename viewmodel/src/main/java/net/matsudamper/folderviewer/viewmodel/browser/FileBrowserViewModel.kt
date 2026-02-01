package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.repository.FavoriteConfiguration
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.PreferencesRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.viewmodel.util.FileUtil
import net.matsudamper.folderviewer.viewmodel.worker.FileUploadWorker
import net.matsudamper.folderviewer.viewmodel.worker.FolderUploadWorker

@HiltViewModel(assistedFactory = FileBrowserViewModel.Companion.Factory::class)
class FileBrowserViewModel @AssistedInject constructor(
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val uploadJobRepository: UploadJobRepository,
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
            val fileId = when (fileObjectId) {
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
                    val displayPath = arg.displayPath.orEmpty()
                    val name = if (displayPath.isEmpty()) {
                        state.storageName ?: "Storage"
                    } else {
                        displayPath.trim('/').split('/').lastOrNull()
                            ?: viewModelStateFlow.value.storageName
                            ?: "null"
                    }

                    storageRepository.addFavorite(
                        storageId = arg.storageId,
                        fileId = fileId,
                        displayPath = displayPath,
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

        override fun onCreateDirectoryClick() {
            viewModelScope.launch {
                uiChannelEvent.send(FileBrowserUiEvent.ShowCreateDirectoryDialog)
            }
        }

        override fun onConfirmCreateDirectory(directoryName: String) {
            viewModelScope.launch {
                runCatching {
                    val repository = getRepository()
                    repository.createDirectory(fileObjectId, directoryName)
                    fetchFilesInternal()
                    uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("Created directory: $directoryName"))
                }.onFailure { e ->
                    when (e) {
                        is CancellationException -> throw e

                        else -> {
                            e.printStackTrace()
                            uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("Failed to create directory"))
                        }
                    }
                }
            }
        }

        override fun onCancelSelection() {
            viewModelStateFlow.update { it.copy(selectedKeys = emptySet()) }
        }
    }

    val uiState: Flow<FileBrowserUiState> = channelFlow {
        viewModelStateFlow.collectLatest { viewModelState ->
            val sortedFiles = viewModelState.rawFiles.sortedWith(createComparator(viewModelState.sortConfig))
            val isSelectionMode = viewModelState.selectedKeys.isNotEmpty()
            val uiItems = sortedFiles.map { fileItem ->
                val isImage = FileUtil.isImage(fileItem.displayPath)
                FileBrowserUiState.UiFileItem.File(
                    name = fileItem.displayPath,
                    key = fileItem.id.id,
                    isDirectory = fileItem.isDirectory,
                    size = fileItem.size,
                    lastModified = fileItem.lastModified,
                    thumbnail = if (isImage) {
                        FileImageSource.Thumbnail(
                            storageId = arg.storageId,
                            fileId = fileItem.id,
                        )
                    } else {
                        null
                    },
                    isSelected = viewModelState.selectedKeys.contains(fileItem.id),
                    callbacks = FileItemCallbacks(fileItem, sortedFiles, isSelectionMode),
                )
            }

            val favoriteItems = viewModelState.favorites.map { favorite ->
                FileBrowserUiState.UiFileItem.File(
                    name = favorite.displayPath,
                    key = favorite.fileId.id,
                    isDirectory = true,
                    size = 0,
                    lastModified = 0,
                    thumbnail = if (FileUtil.isImage(favorite.displayPath)) {
                        FileImageSource.Thumbnail(
                            storageId = arg.storageId,
                            fileId = favorite.fileId,
                        )
                    } else {
                        null
                    },
                    isSelected = false,
                    callbacks = FavoriteItemCallbacks(favorite),
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
                    title = arg.displayPath ?: viewModelState.storageName ?: "null",
                    isFavorite = viewModelState.favoriteId != null,
                    visibleFavoriteButton = arg.displayPath != null,
                    sortConfig = viewModelState.sortConfig,
                    displayConfig = viewModelState.displayConfig,
                    visibleFolderBrowserButton = arg.displayPath != null,
                    isSelectionMode = isSelectionMode,
                    selectedCount = viewModelState.selectedKeys.size,
                    contentState = contentState,
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
            val fileId = when (fileObjectId) {
                is FileObjectId.Root -> return@launch
                is FileObjectId.Item -> fileObjectId.id
            }
            storageRepository.favorites
                .map { favorites ->
                    favorites.find { it.storageId == arg.storageId && it.fileId.id == fileId }?.id
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
                    hasError = false,
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
            val displayPath: String,
            val storageId: StorageId,
            val id: FileObjectId.Item,
        ) : ViewModelEvent

        data class NavigateToImageViewer(
            val id: FileObjectId.Item,
            val storageId: StorageId,
            val allPaths: List<FileObjectId.Item>,
        ) : ViewModelEvent

        data class NavigateToFolderBrowser(
            val id: FileObjectId,
            val displayPath: String?,
            val storageId: StorageId,
        ) : ViewModelEvent

        data object LaunchFilePicker : ViewModelEvent
        data object LaunchFolderPicker : ViewModelEvent

        data class OpenWithExternalPlayer(
            val viewSourceUri: ViewSourceUri,
            val storageId: StorageId,
            val fileId: FileObjectId.Item,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent
    }

    suspend fun handleFileUpload(uri: android.net.Uri, fileName: String) {
        val inputData = Data.Builder()
            .putString(FileUploadWorker.KEY_STORAGE_ID, Json.encodeToString(arg.storageId))
            .putString(FileUploadWorker.KEY_FILE_OBJECT_ID, Json.encodeToString(fileObjectId))
            .putString(FileUploadWorker.KEY_URI, uri.toString())
            .putString(FileUploadWorker.KEY_FILE_NAME, fileName)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
            .setInputData(inputData)
            .addTag(FileUploadWorker.TAG_UPLOAD)
            .build()

        uploadJobRepository.saveJob(
            UploadJobRepository.UploadJob(
                workerId = workRequest.id.toString(),
                name = fileName,
                isFolder = false,
                storageId = arg.storageId,
                fileObjectId = fileObjectId,
                displayPath = arg.displayPath.orEmpty(),
            ),
        )

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("ファイルのアップロードを開始しました"))
    }

    suspend fun handleFolderUpload(folderUri: android.net.Uri) {
        runCatching {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                getApplication(),
                folderUri,
            )
            if (documentFile == null) {
                uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("フォルダの取得に失敗しました"))
                return
            }

            val folderName = documentFile.name
            if (folderName == null) {
                uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("フォルダ名の取得に失敗しました"))
                return
            }

            val existingFiles = viewModelStateFlow.value.rawFiles
            val folderExists = existingFiles.any { it.displayPath == folderName && it.isDirectory }

            if (folderExists) {
                uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("同じ名前のフォルダが既に存在します: $folderName"))
                return
            }

            enqueueFolderUpload(documentFile, folderName)
            uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("フォルダのアップロードを開始しました"))
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("アップロード開始失敗: ${e.message}"))
                }
            }
        }
    }

    private suspend fun enqueueFolderUpload(
        documentFile: androidx.documentfile.provider.DocumentFile,
        folderName: String,
    ) {
        val files = mutableListOf<Pair<android.net.Uri, String>>()
        collectFiles(documentFile, "", files)

        val uriDataList = files.map { (uri, relativePath) ->
            FolderUploadWorker.UriData(uri = uri.toString(), relativePath = relativePath)
        }

        val inputData = Data.Builder()
            .putString(FolderUploadWorker.KEY_STORAGE_ID, Json.encodeToString(arg.storageId))
            .putString(FolderUploadWorker.KEY_FILE_OBJECT_ID, Json.encodeToString(fileObjectId))
            .putString(FolderUploadWorker.KEY_FOLDER_NAME, folderName)
            .putString(FolderUploadWorker.KEY_URI_DATA_LIST, Json.encodeToString(uriDataList))
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<FolderUploadWorker>()
            .setInputData(inputData)
            .addTag(FolderUploadWorker.TAG_UPLOAD)
            .build()

        uploadJobRepository.saveJob(
            UploadJobRepository.UploadJob(
                workerId = uploadWorkRequest.id.toString(),
                name = folderName,
                isFolder = true,
                storageId = arg.storageId,
                fileObjectId = fileObjectId,
                displayPath = arg.displayPath.orEmpty(),
            ),
        )

        WorkManager.getInstance(getApplication()).enqueue(uploadWorkRequest)
    }

    private fun collectFiles(
        folder: androidx.documentfile.provider.DocumentFile,
        relativePath: String,
        files: MutableList<Pair<android.net.Uri, String>>,
    ) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                val newRelativePath = if (relativePath.isEmpty()) {
                    file.name.orEmpty()
                } else {
                    "$relativePath/${file.name}"
                }
                collectFiles(file, newRelativePath, files)
            } else {
                val filePath = if (relativePath.isEmpty()) {
                    file.name.orEmpty()
                } else {
                    "$relativePath/${file.name}"
                }
                file.uri.let { uri ->
                    files.add(uri to filePath)
                }
            }
        }
    }

    private inner class FileItemCallbacks(
        private val fileItem: FileItem,
        private val sortedFiles: List<FileItem>,
        private val isSelectionMode: Boolean,
    ) : FileBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() {
            if (isSelectionMode) {
                toggleSelection(fileItem.id)
                return
            }
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
                val isVideo = FileUtil.isVideo(fileItem.displayPath.lowercase())

                when {
                    isImage -> {
                        viewModelScope.launch {
                            viewModelEventChannel.send(
                                ViewModelEvent.NavigateToImageViewer(
                                    id = fileItem.id,
                                    storageId = arg.storageId,
                                    allPaths = sortedFiles.filter { FileUtil.isImage(it.displayPath) }.map { it.id },
                                ),
                            )
                        }
                    }

                    isVideo -> {
                        viewModelScope.launch {
                            openWithExternalPlayer(fileItem)
                        }
                    }

                    else -> {
                        viewModelScope.launch {
                            openWithExternalPlayer(fileItem)
                        }
                    }
                }
            }
        }

        override fun onLongClick() {
            toggleSelection(fileItem.id)
        }

        override fun onCheckedChange(checked: Boolean) {
            if (checked) {
                viewModelStateFlow.update { it.copy(selectedKeys = it.selectedKeys + fileItem.id) }
            } else {
                viewModelStateFlow.update { it.copy(selectedKeys = it.selectedKeys - fileItem.id) }
            }
        }
    }

    private suspend fun openWithExternalPlayer(fileItem: FileItem) {
        runCatching {
            val repository = getRepository()
            val externalPlayerUri = repository.getViewSourceUri(fileItem.id)
            val mimeType = FileUtil.getMimeType(fileItem.displayPath)

            viewModelEventChannel.send(
                ViewModelEvent.OpenWithExternalPlayer(
                    viewSourceUri = externalPlayerUri,
                    storageId = arg.storageId,
                    fileId = fileItem.id,
                    fileName = fileItem.displayPath,
                    mimeType = mimeType,
                ),
            )
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e

                else -> {
                    e.printStackTrace()
                    uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("外部プレイヤーで開けませんでした: ${e.message}"))
                }
            }
        }
    }

    private inner class FavoriteItemCallbacks(
        private val favorite: FavoriteConfiguration,
    ) : FileBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToFileBrowser(
                        displayPath = favorite.displayPath,
                        storageId = arg.storageId,
                        id = favorite.fileId,
                    ),
                )
            }
        }

        override fun onLongClick() = Unit
        override fun onCheckedChange(checked: Boolean) = Unit
    }

    private fun toggleSelection(fileId: FileObjectId.Item) {
        viewModelStateFlow.update {
            val newKeys = if (it.selectedKeys.contains(fileId)) {
                it.selectedKeys - fileId
            } else {
                it.selectedKeys + fileId
            }
            it.copy(selectedKeys = newKeys)
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
        val hasError: Boolean = false,
        val selectedKeys: Set<FileObjectId.Item> = emptySet(),
    )

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: FileBrowser): FileBrowserViewModel
        }
    }
}
