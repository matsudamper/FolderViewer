package net.matsudamper.folderviewer.viewmodel.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.nio.file.Paths
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.navigation.FolderBrowser
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.PreferencesRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiEvent
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState
import net.matsudamper.folderviewer.viewmodel.FileSortComparator

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val arg: FolderBrowser = savedStateHandle.toRoute<FolderBrowser>()

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val uiChannelEvent = Channel<FolderBrowserUiEvent>()
    val uiEvent: Flow<FolderBrowserUiEvent> = uiChannelEvent.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                currentPath = arg.path,
                folder = ViewModelState.Folder(
                    path = arg.path,
                    files = listOf(),
                    folders = listOf(),
                ),
            ),
        )

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
            viewModelScope.launch {
                preferencesRepository.saveFolderBrowserFolderSortConfig(config.toRepository())
            }
        }

        override fun onFileSortConfigChanged(config: FolderBrowserUiState.FileSortConfig) {
            viewModelStateFlow.update { it.copy(fileSortConfig = config) }
            viewModelScope.launch {
                preferencesRepository.saveFolderBrowserFileSortConfig(config.toRepository())
            }
        }

        override fun onDisplayModeChanged(config: UiDisplayConfig) {
            viewModelStateFlow.update { it.copy(displayConfig = config) }
            viewModelScope.launch {
                preferencesRepository.saveFolderBrowserDisplayMode(
                    when (config.displayMode) {
                        UiDisplayConfig.DisplayMode.List -> PreferencesRepository.DisplayMode.List
                        UiDisplayConfig.DisplayMode.Grid -> PreferencesRepository.DisplayMode.Grid
                    },
                )
            }
        }
    }

    private val uiStateCreator = FolderBrowserUiStateCreator(
        callbacks = callbacks,
        viewModelScope = viewModelScope,
        path = arg.path,
        storageId = arg.storageId,
        viewModelEventChannel = viewModelEventChannel,
    )

    val uiState: Flow<FolderBrowserUiState> = channelFlow {
        viewModelStateFlow.collectLatest { viewModelState ->
            trySend(
                uiStateCreator.create(
                    viewModelState = viewModelState,
                ),
            )
        }
    }

    private var fileRepository: FileRepository? = null
    private var fetchJob: Job? = null

    init {
        refresh()
        loadStorageName()
        viewModelScope.launch {
            collectSortConfig()
        }
        viewModelScope.launch {
            collectDisplayMode()
        }
    }

    private suspend fun collectSortConfig() {
        combine(
            preferencesRepository.folderBrowserFolderSortConfig,
            preferencesRepository.folderBrowserFileSortConfig,
        ) { folderConfig, fileConfig ->
            viewModelStateFlow.update {
                it.copy(
                    folderSortConfig = FolderBrowserUiState.FileSortConfig(
                        key = when (folderConfig.key) {
                            PreferencesRepository.FileSortKey.Name -> FolderBrowserUiState.FileSortKey.Name
                            PreferencesRepository.FileSortKey.Date -> FolderBrowserUiState.FileSortKey.Date
                            PreferencesRepository.FileSortKey.Size -> FolderBrowserUiState.FileSortKey.Size
                        },
                        isAscending = folderConfig.isAscending,
                    ),
                    fileSortConfig = FolderBrowserUiState.FileSortConfig(
                        key = when (fileConfig.key) {
                            PreferencesRepository.FileSortKey.Name -> FolderBrowserUiState.FileSortKey.Name
                            PreferencesRepository.FileSortKey.Date -> FolderBrowserUiState.FileSortKey.Date
                            PreferencesRepository.FileSortKey.Size -> FolderBrowserUiState.FileSortKey.Size
                        },
                        isAscending = fileConfig.isAscending,
                    ),
                )
            }
        }.collect()
    }

    private suspend fun collectDisplayMode() {
        preferencesRepository.folderBrowserDisplayMode.collect { displayMode ->
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

    private fun FolderBrowserUiState.FileSortConfig.toRepository(): PreferencesRepository.FileSortConfig {
        return PreferencesRepository.FileSortConfig(
            key = when (this.key) {
                FolderBrowserUiState.FileSortKey.Name -> PreferencesRepository.FileSortKey.Name
                FolderBrowserUiState.FileSortKey.Date -> PreferencesRepository.FileSortKey.Date
                FolderBrowserUiState.FileSortKey.Size -> PreferencesRepository.FileSortKey.Size
            },
            isAscending = this.isAscending,
        )
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
                    folder = ViewModelState.Folder(
                        path = arg.path,
                        files = listOf(),
                        folders = listOf(),
                    ),
                    isLoading = true,
                    isRefreshing = false,
                )
            }
            try {
                val repository = getRepository()
                fetchAllFilesRecursive(repository, arg.path)
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

    private suspend fun fetchAllFilesRecursive(
        repository: FileRepository,
        path: String,
    ) {
        val items = repository.getFiles(path)
        val files = items.filter { !it.isDirectory }
        // フォルダは読み込み順番に影響するのでフォルダのみソートする。表示はUIState側でソートする
        val folders = items.filter { it.isDirectory }
            .sortedWith(
                FileSortComparator(
                    config = viewModelStateFlow.value.folderSortConfig,
                    sizeProvider = { it.size },
                    lastModifiedProvider = { it.lastModified },
                    nameProvider = { it.name },
                ),
            )
        viewModelStateFlow.update { viewModelState ->
            viewModelState.copy(
                folder = mergeFolder(
                    original = viewModelState.folder,
                    addFolder = ViewModelState.Folder(
                        path = path,
                        files = files,
                        folders = folders.map {
                            ViewModelState.Folder(
                                path = it.path,
                                files = listOf(),
                                folders = listOf(),
                            )
                        },
                    ),
                    addPath = path,
                ),
            )
        }

        for (folder in folders) {
            fetchAllFilesRecursive(repository, folder.path)
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
    }

    private fun mergeFolder(
        original: ViewModelState.Folder,
        addFolder: ViewModelState.Folder,
        addPath: String,
    ): ViewModelState.Folder {
        if (original.path == addPath) {
            return addFolder
        }
        val targetPath = Paths.get(addPath)
        return original.copy(
            folders = original.folders.map { folder ->
                if (targetPath.startsWith(Paths.get(folder.path))) {
                    mergeFolder(
                        original = folder,
                        addFolder = addFolder,
                        addPath = addPath,
                    )
                } else {
                    folder
                }
            },
        )
    }

    data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val currentPath: String = "",
        val storageName: String? = null,
        val folder: Folder,
        val folderSortConfig: FolderBrowserUiState.FileSortConfig = FolderBrowserUiState.FileSortConfig(
            key = FolderBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        val fileSortConfig: FolderBrowserUiState.FileSortConfig = FolderBrowserUiState.FileSortConfig(
            key = FolderBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        val displayConfig: UiDisplayConfig = UiDisplayConfig(
            displayMode = UiDisplayConfig.DisplayMode.List,
            displaySize = UiDisplayConfig.DisplaySize.Medium,
        ),
    ) {
        data class Folder(
            val path: String,
            val files: List<FileItem>,
            val folders: List<Folder>,
        )
    }
}
