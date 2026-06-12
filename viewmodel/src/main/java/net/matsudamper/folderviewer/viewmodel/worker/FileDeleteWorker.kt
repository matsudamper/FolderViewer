package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.repository.DeleteJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltWorker
internal class FileDeleteWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
    private val deleteJobRepository: DeleteJobRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val operationId = inputData.getLong(KEY_DELETE_OPERATION_ID, -1L)
        if (operationId == -1L) return@withContext Result.failure()

        try {
            deleteJobRepository.resetRunningFiles(operationId)
            deleteJobRepository.updateStatus(
                operationId = operationId,
                status = OperationRepository.OperationStatus.RUNNING,
                workerId = id.toString(),
            )
            executeJob(operationId)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                deleteJobRepository.cancelJob(operationId)
            }
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            deleteJobRepository.updateError(
                operationId = operationId,
                errorMessage = e.message,
                errorCause = e.cause?.toString(),
            )
            Result.failure()
        }
    }

    private suspend fun executeJob(operationId: Long): Result {
        val notificationId = DELETE_NOTIFICATION_BASE_ID + operationId.toInt()
        val allFiles = deleteJobRepository.getFiles(operationId)
        val totalFiles = allFiles.count { !it.isDirectory }
        setForeground(createForegroundInfo(notificationId, 0, totalFiles, null))

        val pendingFiles = deleteJobRepository.getPendingFiles(operationId)
        if (pendingFiles.isEmpty()) {
            deleteJobRepository.updateStatus(operationId, OperationRepository.OperationStatus.COMPLETED)
            return Result.success()
        }

        val sortedFiles = pendingFiles.sortedWith(
            compareByDescending<DeleteJobRepository.DeleteFile> { file ->
                file.displayPath().count { c -> c == '/' }
            }.thenBy { it.isDirectory },
        )

        var completedCount = allFiles.count {
            !it.isDirectory && it.status == OperationRepository.FileStatus.COMPLETED
        }

        sortedFiles.forEachIndexed { index, file ->
            if (isStopped) {
                deleteJobRepository.resetRunningFiles(operationId)
                return Result.retry()
            }
            if (index == 0) {
                deleteJobRepository.finishFileAndStartNext(finish = null, nextFileId = file.id)
            }
            val finish = deleteFile(file)
            deleteJobRepository.finishFileAndStartNext(
                finish = finish,
                nextFileId = sortedFiles.getOrNull(index + 1)?.id,
            )
            if (finish is DeleteJobRepository.FileFinish.Completed && !file.isDirectory) {
                completedCount++
            }
            updateNotification(notificationId, completedCount, totalFiles, file.displayPath())
        }

        val finalStatus = if (deleteJobRepository.countFailedFiles(operationId) > 0) {
            OperationRepository.OperationStatus.FAILED
        } else {
            OperationRepository.OperationStatus.COMPLETED
        }
        deleteJobRepository.updateStatus(operationId, finalStatus)
        return Result.success()
    }

    private suspend fun deleteFile(file: DeleteJobRepository.DeleteFile): DeleteJobRepository.FileFinish {
        val storageId = file.sourceFileId.storageId
        val repo = storageRepository.getFileRepository(storageId)
            ?: return DeleteJobRepository.FileFinish.Failed(file.id, "ストレージが見つかりません: $storageId")

        return try {
            if (file.isDirectory) {
                repo.deleteDirectory(file.sourceFileId)
            } else {
                repo.deleteFile(file.sourceFileId)
            }
            DeleteJobRepository.FileFinish.Completed(file.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DeleteJobRepository.FileFinish.Failed(file.id, e.message ?: e.toString())
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val operationId = inputData.getLong(KEY_DELETE_OPERATION_ID, -1L)
        val notificationId = DELETE_NOTIFICATION_BASE_ID + operationId.toInt()
        return createForegroundInfo(notificationId, 0, 0, null)
    }

    private fun createForegroundInfo(
        notificationId: Int,
        completedFiles: Int,
        totalFiles: Int,
        currentFileName: String?,
    ): ForegroundInfo {
        createNotificationChannel()
        val title = "ファイル削除中"
        val text = if (currentFileName != null) {
            "$currentFileName ($completedFiles/$totalFiles)"
        } else {
            "$completedFiles/$totalFiles ファイル"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setProgress(totalFiles, completedFiles, totalFiles == 0)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(
        notificationId: Int,
        completedFiles: Int,
        totalFiles: Int,
        currentFileName: String?,
    ) {
        createNotificationChannel()
        val text = if (currentFileName != null) {
            "$currentFileName ($completedFiles/$totalFiles)"
        } else {
            "$completedFiles/$totalFiles ファイル完了"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ファイル削除中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setProgress(totalFiles, completedFiles, false)
            .setOngoing(true)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun DeleteJobRepository.DeleteFile.displayPath(): String {
        return if (relativePath.isEmpty()) {
            fileName
        } else {
            "$relativePath/$fileName"
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ファイル削除",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ファイルの削除状態を表示します"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "delete_channel"
        private const val DELETE_NOTIFICATION_BASE_ID = 2000

        const val TAG_DELETE = "delete"
        const val KEY_DELETE_OPERATION_ID = "delete_operation_id"
    }
}
