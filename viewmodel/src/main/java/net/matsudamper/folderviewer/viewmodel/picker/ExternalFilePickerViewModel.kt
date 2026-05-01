package net.matsudamper.folderviewer.viewmodel.picker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.ExternalFilePicker
import net.matsudamper.folderviewer.repository.ExternalPickerRepository
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.PreferencesRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.ui.picker.ExternalFilePickerUiEvent
import net.matsudamper.folderviewer.ui.picker.ExternalFilePickerUiState
import net.matsudamper.folderviewer.ui.util.formatBytes
import net.matsudamper.folderviewer.viewmodel.util.FileUtil

@HiltViewModel(assistedFactory = ExternalFilePickerViewModel.Companion.Factory::class)
class ExternalFilePickerViewModel @AssistedInject constructor(
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val externalPickerRepository: ExternalPickerRepository,
    application: Application,
    @Assisted private val arg: ExternalFilePicker,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow: Flow<ViewModelEvent> = viewModelEventChannel.receiveAsFlow()

    private val uiEventChannel = Channel<ExternalFilePickerUiEvent>()
    val uiEvent: Flow<ExternalFilePickerUiEvent> = uiEventChannel.receiveAsFlow()

    private val viewModelStateFlow = MutableStateFlow(ViewModelState())
    private var fileRepository: FileRepository? = null

    private val displayName get() = arg.displayPath ?: viewModelStateFlow.value.storageName.orEmpty()

    private val callbacks = object : ExternalFilePickerUiState.Callbacks {
        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.PopBackStack)
            }
        }

        override fun onRefresh() {
            viewModelScope.launch {
                viewModelStateFlow.update { it.copy(isRefreshing = true) }
                fetchFilesInternal()
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

        override fun onSelectedCountClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateToSelectedList)
            }
        }

        override fun onSubmit() {
            viewModelScope.launch {
                val selectedItems = externalPickerRepository.selectedItems.value.values.toList()
                if (selectedItems.isEmpty()) return@launch
                val results = selectedItems.map { pickerItem ->
                    runCatching {
                        val repo = storageRepository.getFileRepository(pickerItem.id.storageId)
                            ?: error("storage not found: ${pickerItem.id.storageId}")
                        val viewSourceUri = repo.getViewSourceUri(pickerItem.id)
                        val mimeType = FileUtil.getMimeType(pickerItem.name)
                        ViewModelEvent.ReturnMultipleResults.ResultItem(
                            viewSourceUri = viewSourceUri,
                            fileId = pickerItem.id,
                            fileName = pickerItem.name,
                            mimeType = mimeType,
                        )
                    }
                }
                if (results.any { it.isFailure }) {
                    viewModelEventChannel.send(ViewModelEvent.SubmitFailed)
                    return@launch
                }
                viewModelEventChannel.send(ViewModelEvent.ReturnMultipleResults(results.map { it.getOrThrow() }))
            }
        }
    }

    val uiState: Flow<ExternalFilePickerUiState> = combine(
        viewModelStateFlow,
        externalPickerRepository.selectedItems,
    ) { viewModelState, selectedItems ->
        val sortedFiles = viewModelState.rawFiles.sortedWith(createComparator(viewModelState.sortConfig))

        val uiItems = sortedFiles.map { fileItem ->
            val isImage = FileUtil.isImage(fileItem.displayPath)
            FileBrowserUiState.UiFileItem.File(
                name = fileItem.displayPath,
                key = fileItem.id.id,
                isDirectory = fileItem.isDirectory,
                subText = buildSubText(fileItem.isDirectory, fileItem.lastModified, fileItem.size),
                thumbnail = if (isImage) FileImageSource.Thumbnail(fileId = fileItem.id) else null,
                isSelected = selectedItems.containsKey(fileItem.id),
                callbacks = FileItemCallbacks(fileItem, sortedFiles),
            )
        }

        val contentState = when {
            viewModelState.isLoading && uiItems.isEmpty() -> ExternalFilePickerUiState.ContentState.Loading
            viewModelState.hasError && uiItems.isEmpty() -> ExternalFilePickerUiState.ContentState.Error
            uiItems.isEmpty() -> ExternalFilePickerUiState.ContentState.Empty
            else -> ExternalFilePickerUiState.ContentState.Content(files = uiItems)
        }

        ExternalFilePickerUiState(
            title = arg.displayPath ?: viewModelState.storageName ?: "ファイルを選択",
            isRefreshing = viewModelState.isRefreshing,
            isMultipleMode = arg.allowMultiple,
            selectedCount = selectedItems.size,
            sortConfig = viewModelState.sortConfig,
            displayConfig = viewModelState.displayConfig,
            contentState = contentState,
            callbacks = callbacks,
        )
    }

    init {
        loadFiles()
        loadStorageName()
        viewModelScope.launch { loadSortConfig() }
        viewModelScope.launch { loadDisplayMode() }
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
            val files = repository.getFiles(arg.fileId)
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
                        it.copy(isLoading = false, isRefreshing = false, hasError = true)
                    }
                }
            }
        }
    }

    private fun loadStorageName() {
        viewModelScope.launch {
            val storage = storageRepository.storageList.first().find { it.id == arg.fileId.storageId }
            viewModelStateFlow.update { it.copy(storageName = storage?.name) }
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

    private suspend fun getRepository(): FileRepository {
        val current = fileRepository
        if (current != null) return current
        val newRepo = storageRepository.getFileRepository(arg.fileId.storageId)
            ?: throw IllegalStateException("Storage not found")
        fileRepository = newRepo
        return newRepo
    }

    private fun createComparator(config: FileBrowserUiState.FileSortConfig): Comparator<FileItem> {
        val comparator: Comparator<FileItem> = when (config.key) {
            FileBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayPath }
            FileBrowserUiState.FileSortKey.Date -> compareBy { it.lastModified }
            FileBrowserUiState.FileSortKey.Size -> compareBy { it.size }
        }
        val ordered = if (config.isAscending) comparator else comparator.reversed()
        return compareByDescending<FileItem> { it.isDirectory }.then(ordered)
    }

    private fun buildSubText(isDirectory: Boolean, lastModified: Long, size: Long): String {
        val dateText = lastModifiedFormatter.format(
            Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()),
        )
        return if (isDirectory) dateText else "$dateText  ${formatBytes(size)}"
    }

    private inner class FileItemCallbacks(
        private val fileItem: FileItem,
        private val sortedFiles: List<FileItem>,
    ) : FileBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() {
            if (fileItem.isDirectory) {
                viewModelScope.launch {
                    viewModelEventChannel.send(
                        ViewModelEvent.NavigateToExternalFilePicker(
                            displayPath = listOf(displayName, fileItem.displayPath)
                                .filter { it.isNotEmpty() }
                                .joinToString("/"),
                            fileId = fileItem.id,
                        ),
                    )
                }
                return
            }
            if (arg.allowMultiple) {
                externalPickerRepository.toggleItem(
                    ExternalPickerRepository.PickerFileItem(
                        id = fileItem.id,
                        name = fileItem.displayPath,
                        size = fileItem.size,
                        lastModified = fileItem.lastModified,
                    ),
                )
            } else {
                viewModelScope.launch {
                    runCatching {
                        val repo = getRepository()
                        val viewSourceUri = repo.getViewSourceUri(fileItem.id)
                        val mimeType = FileUtil.getMimeType(fileItem.displayPath)
                        viewModelEventChannel.send(
                            ViewModelEvent.ReturnSingleResult(
                                viewSourceUri = viewSourceUri,
                                fileId = fileItem.id,
                                fileName = fileItem.displayPath,
                                mimeType = mimeType,
                            ),
                        )
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                    }
                }
            }
        }

        override fun onLongClick() {
            val isPreviewable = !fileItem.isDirectory &&
                (FileUtil.isImage(fileItem.displayPath) || FileUtil.isVideo(fileItem.displayPath))
            viewModelScope.launch {
                uiEventChannel.send(
                    ExternalFilePickerUiEvent.ShowFilePropertiesDialog(
                        fileId = fileItem.id,
                        name = fileItem.displayPath,
                        size = fileItem.size,
                        lastModified = fileItem.lastModified,
                        isPreviewable = isPreviewable,
                        callbacks = object : ExternalFilePickerUiEvent.ShowFilePropertiesDialog.Callbacks {
                            override fun onPreview() {
                                viewModelScope.launch {
                                    if (FileUtil.isImage(fileItem.displayPath)) {
                                        viewModelEventChannel.send(
                                            ViewModelEvent.NavigateToImageViewer(
                                                fileId = fileItem.id,
                                                allPaths = sortedFiles
                                                    .filter { FileUtil.isImage(it.displayPath) }
                                                    .map { it.id },
                                            ),
                                        )
                                    } else {
                                        openWithExternalPlayer(fileItem)
                                    }
                                }
                            }
                        },
                    ),
                )
            }
        }

        override fun onCheckedChange(checked: Boolean) {
            val pickerItem = ExternalPickerRepository.PickerFileItem(
                id = fileItem.id,
                name = fileItem.displayPath,
                size = fileItem.size,
                lastModified = fileItem.lastModified,
            )
            if (checked) {
                if (!externalPickerRepository.isSelected(fileItem.id)) {
                    externalPickerRepository.toggleItem(pickerItem)
                }
            } else {
                externalPickerRepository.removeItem(fileItem.id)
            }
        }
    }

    private suspend fun openWithExternalPlayer(fileItem: FileItem) {
        runCatching {
            val repo = getRepository()
            val viewSourceUri = repo.getViewSourceUri(fileItem.id)
            val mimeType = FileUtil.getMimeType(fileItem.displayPath)
            viewModelEventChannel.send(
                ViewModelEvent.OpenWithExternalPlayer(
                    viewSourceUri = viewSourceUri,
                    fileId = fileItem.id,
                    fileName = fileItem.displayPath,
                    mimeType = mimeType,
                ),
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    sealed interface ViewModelEvent {
        data object PopBackStack : ViewModelEvent
        data object SubmitFailed : ViewModelEvent
        data class NavigateToExternalFilePicker(
            val displayPath: String,
            val fileId: FileObjectId.Item,
        ) : ViewModelEvent
        data object NavigateToSelectedList : ViewModelEvent
        data class NavigateToImageViewer(
            val fileId: FileObjectId.Item,
            val allPaths: List<FileObjectId.Item>,
        ) : ViewModelEvent
        data class OpenWithExternalPlayer(
            val viewSourceUri: ViewSourceUri,
            val fileId: FileObjectId.Item,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent
        data class ReturnSingleResult(
            val viewSourceUri: ViewSourceUri,
            val fileId: FileObjectId.Item,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent
        data class ReturnMultipleResults(
            val items: List<ResultItem>,
        ) : ViewModelEvent {
            data class ResultItem(
                val viewSourceUri: ViewSourceUri,
                val fileId: FileObjectId.Item,
                val fileName: String,
                val mimeType: String?,
            )
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
        val hasError: Boolean = false,
    )

    companion object {
        private val lastModifiedFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

        @AssistedFactory
        interface Factory {
            fun create(arguments: ExternalFilePicker): ExternalFilePickerViewModel
        }
    }
}
