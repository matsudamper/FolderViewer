package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.FileToUpload
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.UploadJobRepository
import net.matsudamper.folderviewer.repository.UploadProgress

@HiltWorker
internal class FolderUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
    private val uploadJobRepository: UploadJobRepository,
    private val operationNotificationIntentFactory: OperationNotificationIntentFactory,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val fileObjectIdString = inputData.getString(KEY_FILE_OBJECT_ID) ?: return@withContext Result.failure()
            val folderName = inputData.getString(KEY_FOLDER_NAME) ?: return@withContext Result.failure()
            val uriDataListJson = inputData.getString(KEY_URI_DATA_LIST) ?: return@withContext Result.failure()

            setForeground(createForegroundInfo())
            uploadJobRepository.updateStatus(
                workerId = id.toString(),
                status = OperationRepository.OperationStatus.RUNNING,
            )

            val fileObjectId = Json.decodeFromString<FileObjectId>(fileObjectIdString)
            val uriDataList = Json.decodeFromString<List<UriData>>(uriDataListJson)
            val repository = storageRepository.getFileRepository(fileObjectId.storageId)
                ?: throw IllegalStateException("ストレージが見つかりません: ${fileObjectId.storageId}")

            val filesToUpload = getFilesToUpload(uriDataList)
            val uploadEntries = buildUploadEntries(filesToUpload)

            uploadEntries.forEach { entry ->
                val size = entry.file.size
                if (entry.fileRowId != null && size != null) {
                    uploadJobRepository.updateFileSize(entry.fileRowId, size)
                }
            }

            val progressFlow = MutableStateFlow(UploadProgress(0L, 0))
            val progressJob = launch {
                var appliedCompletedCount = 0
                progressFlow.collectLatest { progress ->
                    val completedCount = progress.completedFiles.coerceAtMost(uploadEntries.size)
                    val newlyCompletedIds = uploadEntries.subList(appliedCompletedCount, completedCount)
                        .mapNotNull { it.fileRowId }
                    val runningEntry = uploadEntries.getOrNull(completedCount)
                    val completedSize = uploadEntries.take(completedCount).sumOf { it.file.size ?: 0L }
                    uploadJobRepository.applyFolderProgress(
                        completedFileIds = newlyCompletedIds,
                        runningFileId = runningEntry?.fileRowId,
                        transferredBytes = (progress.uploadedBytes - completedSize).coerceAtLeast(0L),
                    )
                    appliedCompletedCount = completedCount
                }
            }

            try {
                repository.uploadFolder(
                    id = fileObjectId,
                    folderName = folderName,
                    files = filesToUpload,
                    onRead = progressFlow,
                )
            } finally {
                progressJob.cancel()
            }

            uploadJobRepository.completeJob(id.toString())
            OperationResultNotification.notify(
                context = context,
                notificationId = RESULT_NOTIFICATION_BASE_ID + id.hashCode(),
                content = OperationResultNotification.Content(
                    title = "フォルダアップロードが完了しました",
                    text = folderName,
                    smallIcon = android.R.drawable.stat_sys_upload_done,
                ),
                contentIntent = operationNotificationIntentFactory.createUploadDetailIntent(id.toString()),
            )
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                uploadJobRepository.cancelJob(id.toString())
            }
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            uploadJobRepository.updateError(
                workerId = id.toString(),
                errorMessage = e.message,
                errorCause = e.cause?.toString(),
            )
            OperationResultNotification.notify(
                context = context,
                notificationId = RESULT_NOTIFICATION_BASE_ID + id.hashCode(),
                content = OperationResultNotification.Content(
                    title = "フォルダアップロードに失敗しました",
                    text = e.message ?: e.toString(),
                    smallIcon = android.R.drawable.stat_notify_error,
                ),
                contentIntent = operationNotificationIntentFactory.createUploadDetailIntent(id.toString()),
            )
            Result.failure()
        }
    }

    private suspend fun buildUploadEntries(filesToUpload: List<FileToUpload>): List<UploadEntry> {
        val job = uploadJobRepository.getJob(id.toString()) ?: return filesToUpload.map { UploadEntry(it, null) }
        val fileRows = uploadJobRepository.getFiles(job.operationId)
        val rowIdByPath = fileRows.associate { row ->
            val path = if (row.relativePath.isEmpty()) row.fileName else "${row.relativePath}/${row.fileName}"
            path to row.id
        }
        return filesToUpload.map { file ->
            UploadEntry(file = file, fileRowId = rowIdByPath[file.relativePath])
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = CHANNEL_ID
        val title = "フォルダアップロード中"

        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(operationNotificationIntentFactory.createUploadDetailIntent(id.toString()))
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "フォルダアップロード",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "フォルダのアップロード状態を表示します"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun getFileSize(uri: android.net.Uri): Long? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        }
    }

    private fun getFilesToUpload(uriDataList: List<UriData>): List<FileToUpload> {
        return uriDataList.map { uriData ->
            val uri = android.net.Uri.parse(uriData.uri)

            val fileSize = getFileSize(uri)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("ファイルを開けませんでした: ${uriData.relativePath}")
            FileToUpload(
                relativePath = uriData.relativePath,
                inputStream = inputStream,
                size = fileSize,
            )
        }
    }

    private data class UploadEntry(
        val file: FileToUpload,
        val fileRowId: Long?,
    )

    @Serializable
    data class UriData(
        val uri: String,
        val relativePath: String,
    )

    companion object {
        private const val CHANNEL_ID = "folder_upload_channel"
        private const val NOTIFICATION_ID = 2
        private const val RESULT_NOTIFICATION_BASE_ID = 8000

        const val TAG_UPLOAD = "upload"
        const val KEY_FILE_OBJECT_ID = "file_object_id"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_URI_DATA_LIST = "uri_data_list"
    }
}
