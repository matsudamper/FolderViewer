package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltWorker
internal class FileUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("LOG", "doWork")
        return withContext(Dispatchers.IO) {
            try {
                val storageIdString = inputData.getString(KEY_STORAGE_ID) ?: return@withContext Result.failure()
                val fileObjectIdString = inputData.getString(KEY_FILE_OBJECT_ID) ?: return@withContext Result.failure()
                val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
                val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()

                setProgress(
                    androidx.work.Data.Builder()
                        .putString(KEY_STORAGE_ID, storageIdString)
                        .putString(KEY_FILE_OBJECT_ID, fileObjectIdString)
                        .putString(KEY_FILE_NAME, fileName)
                        .build(),
                )

                setForeground(createForegroundInfo())

                val storageId = Json.decodeFromString<StorageId>(storageIdString)
                val fileObjectId = Json.decodeFromString<FileObjectId>(fileObjectIdString)
                val repository = storageRepository.getFileRepository(storageId)
                    ?: return@withContext Result.failure()

                val uri = android.net.Uri.parse(uriString)
                val fileSize = getFileSize(uri)

                val progressFlow = MutableStateFlow(0L)
                val progressJob = launch {
                    progressFlow.collectLatest { uploadedBytes ->
                        val builder = androidx.work.Data.Builder()
                            .putString(KEY_STORAGE_ID, storageIdString)
                            .putString(KEY_FILE_OBJECT_ID, fileObjectIdString)
                            .putString(KEY_FILE_NAME, fileName)
                            .putLong("CurrentBytes", uploadedBytes)
                        if (fileSize != null) {
                            builder.putLong("TotalBytes", fileSize)
                        }
                        setProgress(builder.build())
                    }
                }

                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        repository.uploadFile(
                            id = fileObjectId,
                            fileName = fileName,
                            inputStream = inputStream,
                            onRead = progressFlow,
                        )
                    }
                } finally {
                    progressJob.cancel()
                }

                Result.success()
            } catch (e: Throwable) {
                e.printStackTrace()
                Result.failure()
            }
        }
    }
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = CHANNEL_ID
        val title = "ファイルアップロード中"

        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
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
            "ファイルアップロード",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ファイルのアップロード状態を表示します"
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

    companion object {
        private const val CHANNEL_ID = "file_upload_channel"
        private const val NOTIFICATION_ID = 1

        const val TAG_UPLOAD = "upload"
        const val KEY_STORAGE_ID = "storage_id"
        const val KEY_FILE_OBJECT_ID = "file_object_id"
        const val KEY_URI = "uri"
        const val KEY_FILE_NAME = "file_name"
    }
}
