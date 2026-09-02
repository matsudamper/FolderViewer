package net.matsudamper.folderviewer.viewmodel.browser

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.viewmodel.worker.FileDeleteWorker

@Singleton
class DeleteJobCompletionWatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val operationRepository: OperationRepository,
    private val deleteJobRepository: DeleteJobRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeWatches = ConcurrentHashMap.newKeySet<Long>()

    private val _completionUiEvents = MutableSharedFlow<CompletionUiEvent>(extraBufferCapacity = 8)
    val completionUiEvents = _completionUiEvents.asSharedFlow()

    fun watchJob(operationId: Long, storageId: StorageId) {
        if (!activeWatches.add(operationId)) {
            return
        }
        scope.launch {
            try {
                operationRepository.observeProgressById(operationId)
                    .mapNotNull { it?.status }
                    .first { status ->
                        status == OperationRepository.OperationStatus.COMPLETED ||
                            status == OperationRepository.OperationStatus.FAILED ||
                            status == OperationRepository.OperationStatus.CANCELLED
                    }
                _completionUiEvents.emit(CompletionUiEvent(storageId = storageId))
            } finally {
                activeWatches.remove(operationId)
            }
        }
    }

    fun resume() {
        scope.launch {
            deleteJobRepository.getActiveOperationIds().forEach { operationId ->
                recoverStuckJob(operationId)
            }
        }
    }

    private suspend fun recoverStuckJob(operationId: Long) {
        deleteJobRepository.resetRunningFiles(operationId)
        val inputData = Data.Builder()
            .putLong(FileDeleteWorker.KEY_DELETE_OPERATION_ID, operationId)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<FileDeleteWorker>()
            .setInputData(inputData)
            .addTag(FileDeleteWorker.TAG_DELETE)
            .build()
        deleteJobRepository.updateStatus(
            operationId = operationId,
            status = OperationRepository.OperationStatus.ENQUEUED,
            workerId = workRequest.id.toString(),
        )
        WorkManager.getInstance(context).enqueueUniqueWork(
            "delete_job_$operationId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
        val storageId = deleteJobRepository.getFiles(operationId)
            .firstOrNull()
            ?.sourceFileId
            ?.storageId
            ?: return
        watchJob(operationId, storageId)
    }

    data class CompletionUiEvent(
        val storageId: StorageId,
    )
}
