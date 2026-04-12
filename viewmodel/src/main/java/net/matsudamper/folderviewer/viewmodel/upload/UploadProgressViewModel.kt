package net.matsudamper.folderviewer.viewmodel.upload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.repository.db.OperationEntity
import net.matsudamper.folderviewer.ui.upload.UploadProgressUiState
import net.matsudamper.folderviewer.viewmodel.worker.FilePasteWorker
import net.matsudamper.folderviewer.viewmodel.worker.FileUploadWorker
import net.matsudamper.folderviewer.viewmodel.worker.FolderUploadWorker

@HiltViewModel
class UploadProgressViewModel @Inject constructor(
    application: Application,
    private val operationRepository: OperationRepository,
    private val pasteJobRepository: PasteJobRepository,
    private val deleteJobRepository: DeleteJobRepository,
) : AndroidViewModel(application) {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow = MutableStateFlow(ViewModelState())

    private val showClearConfirmDialog = MutableStateFlow(false)

    private val callbacks = object : UploadProgressUiState.Callbacks {
        override fun onBackClick() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.NavigateBack)
            }
        }

        override fun onItemClick(item: UploadProgressUiState.UploadItem) {
            viewModelScope.launch {
                when (item) {
                    is UploadProgressUiState.UploadItem.Paste -> {
                        val jobId = item.id.toLongOrNull() ?: return@launch
                        viewModelEventChannel.send(ViewModelEvent.NavigateToPasteDetail(jobId))
                    }
                    is UploadProgressUiState.UploadItem.Delete -> {
                        val opId = item.id.toLongOrNull() ?: return@launch
                        viewModelEventChannel.send(ViewModelEvent.NavigateToDeleteDetail(opId))
                    }
                    else -> {
                        val uuid = runCatching { UUID.fromString(item.id) }.getOrNull()
                            ?: return@launch
                        viewModelEventChannel.send(
                            ViewModelEvent.NavigateToUploadDetail(workerId = uuid.toString()),
                        )
                    }
                }
            }
        }

        override fun onClearHistoryClick() {
            showClearConfirmDialog.value = true
        }

        override fun onClearHistoryConfirm() {
            showClearConfirmDialog.value = false
            viewModelScope.launch {
                operationRepository.deleteNonActiveHistory()
            }
        }

        override fun onClearHistoryDismiss() {
            showClearConfirmDialog.value = false
        }
    }

    val uiState: StateFlow<UploadProgressUiState> = MutableStateFlow(
        UploadProgressUiState(
            uploadItems = emptyList(),
            showClearConfirmDialog = false,
            callbacks = callbacks,
        ),
    ).also { mutableState ->
        viewModelScope.launch {
            combine(
                viewModelStateFlow,
                showClearConfirmDialog,
            ) { state, showDialog ->
                state to showDialog
            }.collectLatest { (state, showDialog) ->
                val items = state.operations.map { op ->
                    mapOperationToUiItem(op, state.workInfoMap)
                }
                mutableState.value = UploadProgressUiState(
                    uploadItems = items,
                    showClearConfirmDialog = showDialog,
                    callbacks = callbacks,
                )
            }
        }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(getApplication())
            combine(
                operationRepository.observeAll(),
                workManager.getWorkInfosByTagFlow(FileUploadWorker.TAG_UPLOAD),
            ) { operations, workInfoList ->
                val workInfoMap = workInfoList.associateBy { it.id.toString() }
                ViewModelState(
                    operations = operations,
                    workInfoMap = workInfoMap,
                )
            }.collect { state ->
                viewModelStateFlow.value = state
            }
        }
    }

    private suspend fun mapOperationToUiItem(
        op: OperationEntity,
        workInfoMap: Map<String, WorkInfo>,
    ): UploadProgressUiState.UploadItem {
        return when (op.type) {
            OperationRepository.OperationType.UPLOAD_FILE.name,
            OperationRepository.OperationType.UPLOAD_FOLDER.name,
            -> mapUploadOperation(op, workInfoMap)

            OperationRepository.OperationType.PASTE.name -> mapPasteOperation(op)

            OperationRepository.OperationType.DELETE.name -> mapDeleteOperation(op)

            else -> mapUploadOperation(op, workInfoMap)
        }
    }

    private fun mapUploadOperation(
        op: OperationEntity,
        workInfoMap: Map<String, WorkInfo>,
    ): UploadProgressUiState.UploadItem {
        val workInfo = op.workerId?.let { workInfoMap[it] }
        val uploadState = when (workInfo?.state) {
            WorkInfo.State.ENQUEUED -> UploadProgressUiState.UploadState.ENQUEUED
            WorkInfo.State.RUNNING -> UploadProgressUiState.UploadState.RUNNING
            WorkInfo.State.SUCCEEDED -> UploadProgressUiState.UploadState.SUCCEEDED
            WorkInfo.State.FAILED -> UploadProgressUiState.UploadState.FAILED
            WorkInfo.State.BLOCKED -> UploadProgressUiState.UploadState.ENQUEUED
            WorkInfo.State.CANCELLED -> UploadProgressUiState.UploadState.CANCELLED
            null -> when (op.status) {
                OperationRepository.OperationStatus.RUNNING.name -> UploadProgressUiState.UploadState.RUNNING
                OperationRepository.OperationStatus.ENQUEUED.name -> UploadProgressUiState.UploadState.ENQUEUED
                OperationRepository.OperationStatus.PAUSED.name -> UploadProgressUiState.UploadState.ENQUEUED
                OperationRepository.OperationStatus.FAILED.name -> UploadProgressUiState.UploadState.FAILED
                OperationRepository.OperationStatus.CANCELLED.name -> UploadProgressUiState.UploadState.CANCELLED
                else -> if (op.errorMessage != null) {
                    UploadProgressUiState.UploadState.FAILED
                } else {
                    UploadProgressUiState.UploadState.SUCCEEDED
                }
            }
        }

        val currentBytes = workInfo?.progress?.getLong(FolderUploadWorker.KEY_CURRENT_BYTES, 0L) ?: 0L
        val totalBytes = workInfo?.progress?.getLong("TotalBytes", 0L) ?: 0L
        val progress = if (totalBytes > 0) {
            currentBytes.toFloat() / totalBytes.toFloat()
        } else {
            null
        }
        val progressText = if (uploadState == UploadProgressUiState.UploadState.RUNNING) {
            "${formatFileSize(currentBytes)}/${formatFileSize(totalBytes)}"
        } else {
            null
        }

        val isFolder = op.type == OperationRepository.OperationType.UPLOAD_FOLDER.name
        val fileCount = getUploadFileCount(op, isFolder, workInfo)
        val description = getUploadDescription(op, isFolder, uploadState, workInfo)
        val id = op.workerId ?: op.id.toString()
        return if (isFolder) {
            UploadProgressUiState.UploadItem.Folder(
                id = id,
                name = "アップロード ${fileCount}件",
                description = description,
                state = uploadState,
                canNavigate = true,
                fileCount = fileCount,
                progress = progress,
                progressText = progressText,
            )
        } else {
            UploadProgressUiState.UploadItem.File(
                id = id,
                name = "アップロード ${fileCount}件",
                description = description,
                state = uploadState,
                canNavigate = true,
                progress = progress,
                progressText = progressText,
            )
        }
    }

    private suspend fun mapPasteOperation(op: OperationEntity): UploadProgressUiState.UploadItem {
        val pasteState = when {
            op.failedFiles > 0 -> UploadProgressUiState.UploadState.FAILED
            op.status == OperationRepository.OperationStatus.ENQUEUED.name -> UploadProgressUiState.UploadState.ENQUEUED
            op.status == OperationRepository.OperationStatus.RUNNING.name -> UploadProgressUiState.UploadState.RUNNING
            op.status == OperationRepository.OperationStatus.PAUSED.name -> UploadProgressUiState.UploadState.PAUSED
            op.status == OperationRepository.OperationStatus.COMPLETED.name -> UploadProgressUiState.UploadState.SUCCEEDED
            op.status == OperationRepository.OperationStatus.FAILED.name -> UploadProgressUiState.UploadState.FAILED
            op.status == OperationRepository.OperationStatus.WAITING_RESOLUTION.name -> UploadProgressUiState.UploadState.WAITING_RESOLUTION
            else -> UploadProgressUiState.UploadState.FAILED
        }

        val isActive = pasteState == UploadProgressUiState.UploadState.RUNNING ||
            pasteState == UploadProgressUiState.UploadState.PAUSED

        val overallProgress = if (isActive && op.totalBytes > 0) {
            (op.completedBytes + op.currentFileBytes).toFloat() / op.totalBytes.toFloat()
        } else {
            null
        }

        val currentFileProgress = if (isActive && op.currentFileTotalBytes > 0) {
            op.currentFileBytes.toFloat() / op.currentFileTotalBytes.toFloat()
        } else {
            null
        }

        val job = runCatching { pasteJobRepository.getJobById(op.id) }.getOrNull()
        val files = runCatching { pasteJobRepository.getFiles(op.id) }.getOrDefault(emptyList())
        val operationModeName = when (job?.mode) {
            ClipboardRepository.ClipboardMode.Copy -> "コピー"
            ClipboardRepository.ClipboardMode.Cut -> "カット"
            null -> if (op.name.contains("カット")) "カット" else "コピー"
        }
        val notCompletedFiles = op.totalFiles - op.completedFiles - op.duplicateFiles

        val progressText = when {
            pasteState == UploadProgressUiState.UploadState.WAITING_RESOLUTION -> {
                if (op.duplicateFiles > 0) {
                    "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了 ${op.duplicateFiles}重複"
                } else {
                    "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了"
                }
            }
            pasteState == UploadProgressUiState.UploadState.RUNNING -> {
                val completed = formatFileSize(op.completedBytes + op.currentFileBytes)
                val total = formatFileSize(op.totalBytes)
                "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了 ${notCompletedFiles}未完了 ($completed/$total)"
            }
            pasteState == UploadProgressUiState.UploadState.SUCCEEDED -> {
                if (op.duplicateFiles > 0) {
                    "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了 ${op.duplicateFiles}重複"
                } else {
                    "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了"
                }
            }
            pasteState == UploadProgressUiState.UploadState.FAILED -> {
                "$operationModeName ${op.totalFiles}件 - ${op.completedFiles}完了 ${op.failedFiles}失敗"
            }
            else -> {
                "$operationModeName ${op.totalFiles}件"
            }
        }
        val currentFileName = op.currentFileName
        val description = if (pasteState == UploadProgressUiState.UploadState.RUNNING && currentFileName != null) {
            currentFileName
        } else {
            buildPasteDescription(files, currentFileName ?: op.name)
        }

        val pasteCallbacks = object : UploadProgressUiState.PasteCallbacks {
            override fun onPauseClick() {
                pausePasteJob(op)
            }

            override fun onResumeClick() {
                resumePasteJob(op)
            }
        }

        return UploadProgressUiState.UploadItem.Paste(
            id = op.id.toString(),
            name = "$operationModeName ${op.totalFiles}件",
            description = description,
            state = pasteState,
            canNavigate = true,
            mode = op.name,
            operationMode = operationModeName,
            totalFiles = op.totalFiles,
            completedFiles = op.completedFiles,
            failedFiles = op.failedFiles,
            duplicateFiles = op.duplicateFiles,
            firstFileName = op.currentFileName,
            currentFileName = op.currentFileName,
            currentFileProgress = currentFileProgress,
            progress = overallProgress,
            progressText = progressText,
            isPausable = pasteState == UploadProgressUiState.UploadState.RUNNING,
            isResumable = pasteState == UploadProgressUiState.UploadState.PAUSED,
            pasteCallbacks = pasteCallbacks,
        )
    }

    private suspend fun mapDeleteOperation(op: OperationEntity): UploadProgressUiState.UploadItem {
        val deleteState = when (op.status) {
            OperationRepository.OperationStatus.ENQUEUED.name -> UploadProgressUiState.UploadState.ENQUEUED
            OperationRepository.OperationStatus.RUNNING.name -> UploadProgressUiState.UploadState.RUNNING
            OperationRepository.OperationStatus.PAUSED.name -> UploadProgressUiState.UploadState.PAUSED
            OperationRepository.OperationStatus.COMPLETED.name -> UploadProgressUiState.UploadState.SUCCEEDED
            OperationRepository.OperationStatus.FAILED.name -> UploadProgressUiState.UploadState.FAILED
            OperationRepository.OperationStatus.CANCELLED.name -> UploadProgressUiState.UploadState.CANCELLED
            else -> UploadProgressUiState.UploadState.FAILED
        }

        val progress = if (deleteState == UploadProgressUiState.UploadState.RUNNING && op.totalFiles > 0) {
            op.completedFiles.toFloat() / op.totalFiles.toFloat()
        } else {
            null
        }

        val notCompletedFiles = op.totalFiles - op.completedFiles

        val progressText = when {
            deleteState == UploadProgressUiState.UploadState.RUNNING -> {
                "削除 ${op.totalFiles}件 - ${op.completedFiles}完了 ${notCompletedFiles}未完了"
            }
            deleteState == UploadProgressUiState.UploadState.SUCCEEDED -> {
                "削除 ${op.totalFiles}件 - ${op.completedFiles}完了"
            }
            deleteState == UploadProgressUiState.UploadState.FAILED -> {
                "削除 ${op.totalFiles}件 - ${op.completedFiles}完了 ${op.failedFiles}失敗"
            }
            else -> {
                "削除 ${op.totalFiles}件"
            }
        }
        val files = runCatching { deleteJobRepository.getFiles(op.id) }.getOrDefault(emptyList())
        val currentFileName = op.currentFileName
        val description = if (deleteState == UploadProgressUiState.UploadState.RUNNING && currentFileName != null) {
            currentFileName
        } else {
            buildDeleteDescription(files, currentFileName ?: op.name)
        }

        return UploadProgressUiState.UploadItem.Delete(
            id = op.id.toString(),
            name = "削除 ${op.totalFiles}件",
            description = description,
            state = deleteState,
            canNavigate = true,
            totalFiles = op.totalFiles,
            completedFiles = op.completedFiles,
            failedFiles = op.failedFiles,
            firstFileName = op.currentFileName,
            currentFileName = op.currentFileName,
            progress = progress,
            progressText = progressText,
        )
    }

    private fun getUploadFileCount(op: OperationEntity, isFolder: Boolean, workInfo: WorkInfo?): Int {
        return when {
            !isFolder -> op.totalFiles.takeIf { it > 0 } ?: 1
            op.totalFiles > 0 -> op.totalFiles
            else -> extractFolderUploadFileNames(workInfo)?.size ?: 1
        }
    }

    private fun getUploadDescription(
        op: OperationEntity,
        isFolder: Boolean,
        uploadState: UploadProgressUiState.UploadState,
        workInfo: WorkInfo?,
    ): String {
        if (uploadState == UploadProgressUiState.UploadState.RUNNING) {
            val currentFileName = if (isFolder) {
                extractCurrentFolderUploadFileName(workInfo)
            } else {
                workInfo?.progress?.getString(FileUploadWorker.KEY_FILE_NAME)
            }
            if (currentFileName != null) return currentFileName
        }
        return op.name
    }

    private fun extractCurrentFolderUploadFileName(workInfo: WorkInfo?): String? {
        val fileNames = extractFolderUploadFileNames(workInfo) ?: return null
        val completedFiles = workInfo?.progress?.getInt(FolderUploadWorker.KEY_COMPLETED_FILES, 0) ?: 0
        return fileNames.getOrNull(completedFiles)
    }

    private fun extractFolderUploadFileNames(workInfo: WorkInfo?): List<String>? {
        val fileNamesJson = workInfo?.progress?.getString(FolderUploadWorker.KEY_FILE_NAMES) ?: return null
        return runCatching {
            Json.decodeFromString<List<String>>(fileNamesJson)
        }.getOrNull()
    }

    private fun buildPasteDescription(files: List<PasteJobRepository.PasteFile>, fallback: String): String {
        val directory = files
            .filter { it.isDirectory }
            .minWithOrNull(compareBy<PasteJobRepository.PasteFile>({ pathDepth(it.displayPath()) }, { it.id }))
        if (directory != null) return directory.displayPath()

        val fileCount = files.count { !it.isDirectory }
        val file = files
            .filter { !it.isDirectory }
            .minWithOrNull(compareBy<PasteJobRepository.PasteFile>({ pathDepth(it.displayPath()) }, { it.id }))
            ?: return fallback

        return if (fileCount > 1) "${file.displayPath()} 他" else file.displayPath()
    }

    private fun buildDeleteDescription(files: List<DeleteJobRepository.DeleteFile>, fallback: String): String {
        val directory = files
            .filter { it.isDirectory }
            .minWithOrNull(compareBy<DeleteJobRepository.DeleteFile>({ pathDepth(it.displayPath()) }, { it.id }))
        if (directory != null) return directory.displayPath()

        val fileCount = files.count { !it.isDirectory }
        val file = files
            .filter { !it.isDirectory }
            .minWithOrNull(compareBy<DeleteJobRepository.DeleteFile>({ pathDepth(it.displayPath()) }, { it.id }))
            ?: return fallback

        return if (fileCount > 1) "${file.displayPath()} 他" else file.displayPath()
    }

    private fun PasteJobRepository.PasteFile.displayPath(): String {
        return if (destinationRelativePath.isEmpty()) {
            fileName
        } else {
            "$destinationRelativePath/$fileName"
        }
    }

    private fun DeleteJobRepository.DeleteFile.displayPath(): String {
        return if (parentRelativePath.isEmpty()) {
            fileName
        } else {
            "$parentRelativePath/$fileName"
        }
    }

    private fun pathDepth(path: String): Int {
        return path.count { it == '/' }
    }

    private fun pausePasteJob(op: OperationEntity) {
        viewModelScope.launch {
            val workerId = op.workerId ?: return@launch
            val uuid = runCatching { UUID.fromString(workerId) }.getOrNull() ?: return@launch
            WorkManager.getInstance(getApplication()).cancelWorkById(uuid)
        }
    }

    private fun resumePasteJob(op: OperationEntity) {
        viewModelScope.launch {
            val inputData = Data.Builder()
                .putLong(FilePasteWorker.KEY_PASTE_JOB_ID, op.id)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FilePasteWorker>()
                .setInputData(inputData)
                .addTag(FilePasteWorker.TAG_PASTE)
                .build()

            operationRepository.updateStatusAndWorkerId(
                id = op.id,
                status = OperationRepository.OperationStatus.RUNNING,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
        }
    }

    sealed interface ViewModelEvent {
        data object NavigateBack : ViewModelEvent
        data class NavigateToUploadDetail(val workerId: String) : ViewModelEvent
        data class NavigateToPasteDetail(val jobId: Long) : ViewModelEvent
        data class NavigateToDeleteDetail(val opId: Long) : ViewModelEvent
    }

    private data class ViewModelState(
        val operations: List<OperationEntity> = emptyList(),
        val workInfoMap: Map<String, WorkInfo> = emptyMap(),
    )

    private fun formatFileSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> String.format(Locale.ROOT, "%.1fGB", bytes / gb)
            bytes >= mb -> String.format(Locale.ROOT, "%.1fMB", bytes / mb)
            else -> String.format(Locale.ROOT, "%.1fKB", bytes / kb)
        }
    }
}
