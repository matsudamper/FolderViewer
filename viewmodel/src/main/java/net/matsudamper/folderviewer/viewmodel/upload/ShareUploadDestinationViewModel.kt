package net.matsudamper.folderviewer.viewmodel.upload

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.ShareUploadDestination
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.ShareUploadRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.ui.upload.ShareUploadDestinationUiState
import net.matsudamper.folderviewer.viewmodel.worker.FileUploadWorker

@HiltViewModel(assistedFactory = ShareUploadDestinationViewModel.Companion.Factory::class)
class ShareUploadDestinationViewModel @AssistedInject constructor(
    private val storageRepository: StorageRepository,
    private val shareUploadRepository: ShareUploadRepository,
    private val uploadJobRepository: UploadJobRepository,
    application: Application,
    @Assisted private val arg: ShareUploadDestination,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow: Flow<ViewModelEvent> = viewModelEventChannel.receiveAsFlow()

    private val fileObjectId = arg.fileId
    private val viewModelStateFlow = MutableStateFlow(ViewModelState())
    private var fileRepository: FileRepository? = null

    private val displayName get() = arg.displayPath ?: viewModelStateFlow.value.storageName.orEmpty()

    private val callbacks = object : ShareUploadDestinationUiState.Callbacks {
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

        override fun onUploadHere() {
            val files = shareUploadRepository.pendingFiles.value.orEmpty()
            if (files.isEmpty()) return
            viewModelScope.launch {
                val failed = mutableListOf<ShareUploadRepository.PendingFile>()
                var enqueuedCount = 0
                files.forEach { file ->
                    runCatching { enqueueFileUpload(file.uri, file.fileName) }
                        .onSuccess { enqueuedCount++ }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            e.printStackTrace()
                            failed.add(file)
                        }
                }
                if (failed.isEmpty()) {
                    shareUploadRepository.clear()
                    viewModelEventChannel.send(
                        ViewModelEvent.FinishWithMessage("${enqueuedCount}件のアップロードを開始しました"),
                    )
                } else {
                    shareUploadRepository.setPendingFiles(failed)
                    viewModelEventChannel.send(
                        ViewModelEvent.ShowMessage("${failed.size}件の開始に失敗しました。再試行してください"),
                    )
                }
            }
        }
    }

    val uiState: Flow<ShareUploadDestinationUiState> = combine(
        viewModelStateFlow,
        shareUploadRepository.pendingFiles,
    ) { viewModelState, pendingFiles ->
        val folders = viewModelState.rawFiles
            .filter { it.isDirectory }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayPath })
            .map { fileItem ->
                ShareUploadDestinationUiState.Folder(
                    name = fileItem.displayPath,
                    key = fileItem.id.id,
                    callbacks = FolderCallbacks(fileItem),
                )
            }

        val contentState = when {
            viewModelState.isLoading && folders.isEmpty() -> ShareUploadDestinationUiState.ContentState.Loading
            viewModelState.hasError && folders.isEmpty() -> ShareUploadDestinationUiState.ContentState.Error
            folders.isEmpty() -> ShareUploadDestinationUiState.ContentState.Empty
            else -> ShareUploadDestinationUiState.ContentState.Content(folders = folders)
        }

        val pendingCount = pendingFiles?.size ?: 0
        ShareUploadDestinationUiState(
            title = arg.displayPath ?: viewModelState.storageName ?: "アップロード先を選択",
            pendingCount = pendingCount,
            canUpload = viewModelState.rootWritable && pendingCount > 0,
            isRefreshing = viewModelState.isRefreshing,
            contentState = contentState,
            callbacks = callbacks,
        )
    }

    init {
        loadFiles()
        loadStorageName()
        viewModelScope.launch {
            val rootWritable = storageRepository.isRootWritable(fileObjectId)
            viewModelStateFlow.update { it.copy(rootWritable = rootWritable) }
        }
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
                it.copy(isLoading = false, isRefreshing = false, rawFiles = files, hasError = false)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            e.printStackTrace()
            viewModelStateFlow.update {
                it.copy(isLoading = false, isRefreshing = false, hasError = true)
            }
        }
    }

    private fun loadStorageName() {
        viewModelScope.launch {
            val storage = storageRepository.storageList.first().find { it.id == fileObjectId.storageId }
            viewModelStateFlow.update { it.copy(storageName = storage?.name) }
        }
    }

    private suspend fun getRepository(): FileRepository {
        val current = fileRepository
        if (current != null) return current
        val newRepo = storageRepository.getFileRepository(fileObjectId.storageId)
            ?: throw IllegalStateException("Storage not found")
        fileRepository = newRepo
        return newRepo
    }

    private suspend fun enqueueFileUpload(uri: android.net.Uri, fileName: String) {
        val inputData = Data.Builder()
            .putString(FileUploadWorker.KEY_FILE_OBJECT_ID, Json.encodeToString(fileObjectId))
            .putString(FileUploadWorker.KEY_URI, uri.toString())
            .putString(FileUploadWorker.KEY_FILE_NAME, fileName)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
            .setInputData(inputData)
            .addTag(FileUploadWorker.TAG_UPLOAD)
            .build()

        uploadJobRepository.createJob(
            UploadJobRepository.NewUploadJob(
                workerId = workRequest.id.toString(),
                name = fileName,
                isFolder = false,
                fileObjectId = fileObjectId,
                displayPath = arg.displayPath.orEmpty(),
                files = listOf(UploadJobRepository.NewUploadFile(fileName = fileName)),
            ),
        )

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }

    private inner class FolderCallbacks(
        private val fileItem: FileItem,
    ) : ShareUploadDestinationUiState.Folder.Callbacks {
        override fun onClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(
                    ViewModelEvent.NavigateToDestination(
                        displayPath = listOf(displayName, fileItem.displayPath)
                            .filter { it.isNotEmpty() }
                            .joinToString("/"),
                        fileId = fileItem.id,
                    ),
                )
            }
        }
    }

    sealed interface ViewModelEvent {
        data object PopBackStack : ViewModelEvent
        data class NavigateToDestination(
            val displayPath: String,
            val fileId: FileObjectId.Item,
        ) : ViewModelEvent
        data class FinishWithMessage(val message: String) : ViewModelEvent
        data class ShowMessage(val message: String) : ViewModelEvent
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val storageName: String? = null,
        val rawFiles: List<FileItem> = emptyList(),
        val rootWritable: Boolean = false,
        val hasError: Boolean = false,
    )

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: ShareUploadDestination): ShareUploadDestinationViewModel
        }
    }
}
