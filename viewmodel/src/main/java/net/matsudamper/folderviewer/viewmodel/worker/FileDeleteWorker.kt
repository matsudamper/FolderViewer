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

        val job = deleteJobRepository.getJobById(operationId) ?: return@withContext Result.failure()
        val notificationId = DELETE_NOTIFICATION_BASE_ID + operationId.toInt()

        try {
            deleteJobRepository.updateStatus(operationId, OperationRepository.OperationStatus.RUNNING, workerId = id.toString())
            setForeground(createForegroundInfo(notificationId, 0, job.totalFiles, null))

            val allFiles = deleteJobRepository.getPendingFiles(operationId)
            val pendingFiles = allFiles.filter { !it.completed }

            if (pendingFiles.isEmpty()) {
                deleteJobRepository.updateStatus(operationId, OperationRepository.OperationStatus.COMPLETED)
                return@withContext Result.success()
            }

            val sortedFiles = pendingFiles.sortedWith(
                compareByDescending<DeleteJobRepository.DeleteFile> { file ->
                    val ownPath = if (file.parentRelativePath.isEmpty()) {
                        file.fileName
                    } else {
                        "${file.parentRelativePath}/${file.fileName}"
                    }
                    ownPath.count { c -> c == '/' }
                }.thenBy { it.isDirectory },
            )

            var completedFiles = allFiles.count { it.completed && !it.isDirectory }
            var completedBytes = allFiles.filter { it.completed && !it.isDirectory }.sumOf { it.fileSize }
            var failedFiles = 0

            for (file in sortedFiles) {
                if (isStopped) {
                    return@withContext Result.retry()
                }

                val currentFileName = file.displayPath()
                deleteJobRepository.updateProgress(
                    operationId = operationId,
                    completedFiles = completedFiles,
                    completedBytes = completedBytes,
                    failedFiles = failedFiles,
                    currentFileName = currentFileName,
                )

                val storageId = file.sourceFileId.storageId
                val repo = storageRepository.getFileRepository(storageId)

                if (repo == null) {
                    deleteJobRepository.markFileFailed(file.id, "ストレージが見つかりません: $storageId")
                    failedFiles++
                    deleteJobRepository.updateProgress(
                        operationId = operationId,
                        completedFiles = completedFiles,
                        completedBytes = completedBytes,
                        failedFiles = failedFiles,
                        currentFileName = currentFileName,
                    )
                    continue
                }

                try {
                    if (file.isDirectory) {
                        repo.deleteDirectory(file.sourceFileId)
                    } else {
                        repo.deleteFile(file.sourceFileId)
                    }
                    deleteJobRepository.markFileCompleted(file.id)
                    if (!file.isDirectory) {
                        completedFiles++
                        completedBytes += file.fileSize
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    deleteJobRepository.markFileFailed(file.id, e.message ?: e.toString())
                    failedFiles++
                }

                deleteJobRepository.updateProgress(
                    operationId = operationId,
                    completedFiles = completedFiles,
                    completedBytes = completedBytes,
                    failedFiles = failedFiles,
                    currentFileName = currentFileName,
                )
                updateNotification(notificationId, completedFiles, job.totalFiles, currentFileName)
            }

            val finalStatus = if (failedFiles > 0) {
                OperationRepository.OperationStatus.FAILED
            } else {
                OperationRepository.OperationStatus.COMPLETED
            }
            deleteJobRepository.updateStatus(operationId, finalStatus)
            Result.success()
        } catch (e: CancellationException) {
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
        return if (parentRelativePath.isEmpty()) {
            fileName
        } else {
            "$parentRelativePath/$fileName"
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
