package net.matsudamper.folderviewer.viewmodel.browser

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiEvent
import net.matsudamper.folderviewer.viewmodel.worker.FileDeleteWorker

internal class FileBrowserDeleteCoordinator(
    private val dependencies: Dependencies,
) {
    fun observeCompletionEvents(scope: CoroutineScope) {
        scope.launch {
            dependencies.deleteJobCompletionWatcher.completionUiEvents.collect { event ->
                if (event.storageId == dependencies.fileObjectId.storageId) {
                    dependencies.refreshFiles()
                }
            }
        }
    }

    suspend fun handleDelete(items: List<FileItem>) {
        runCatching {
            val repository = dependencies.getRepository()
            val files = items.flatMap { collectDeleteFiles(repository, it, "") }
            val jobName = "${items.size}件を削除"
            val operationId = dependencies.deleteJobRepository.createJob(name = jobName, files = files)

            val inputData = Data.Builder()
                .putLong(FileDeleteWorker.KEY_DELETE_OPERATION_ID, operationId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FileDeleteWorker>()
                .setInputData(inputData)
                .addTag(FileDeleteWorker.TAG_DELETE)
                .build()

            dependencies.deleteJobRepository.updateStatus(
                operationId = operationId,
                status = OperationRepository.OperationStatus.ENQUEUED,
                workerId = workRequest.id.toString(),
            )

            WorkManager.getInstance(dependencies.application).enqueueUniqueWork(
                "delete_job_$operationId",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )

            dependencies.deleteJobCompletionWatcher.watchJob(
                operationId = operationId,
                storageId = dependencies.fileObjectId.storageId,
            )

            dependencies.uiChannelEvent.send(FileBrowserUiEvent.ShowSnackbar("削除を開始しました", showAction = true))
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    e.printStackTrace()
                    dependencies.uiChannelEvent.trySend(FileBrowserUiEvent.ShowSnackbar("削除開始失敗: ${e.message}"))
                }
            }
        }
    }

    private suspend fun collectDeleteFiles(
        repo: FileRepository,
        item: FileItem,
        parentRelativePath: String,
    ): List<DeleteJobRepository.NewDeleteFile> {
        return if (item.isDirectory) {
            val children = repo.getFiles(item.id)
            val dirPath = if (parentRelativePath.isEmpty()) item.displayPath else "$parentRelativePath/${item.displayPath}"
            val childFiles = children.flatMap { child -> collectDeleteFiles(repo, child, dirPath) }
            childFiles + DeleteJobRepository.NewDeleteFile(
                sourceFileId = item.id,
                fileName = item.displayPath,
                fileSize = 0,
                isDirectory = true,
                relativePath = parentRelativePath,
            )
        } else {
            listOf(
                DeleteJobRepository.NewDeleteFile(
                    sourceFileId = item.id,
                    fileName = item.displayPath,
                    fileSize = item.size,
                    isDirectory = false,
                    relativePath = parentRelativePath,
                ),
            )
        }
    }

    data class Dependencies(
        val application: Application,
        val deleteJobRepository: DeleteJobRepository,
        val deleteJobCompletionWatcher: DeleteJobCompletionWatcher,
        val fileObjectId: FileObjectId,
        val uiChannelEvent: Channel<FileBrowserUiEvent>,
        val getRepository: suspend () -> FileRepository,
        val refreshFiles: suspend () -> Unit,
    )
}
