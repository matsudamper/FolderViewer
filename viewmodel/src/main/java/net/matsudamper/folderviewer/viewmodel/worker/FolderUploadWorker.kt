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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.FileToUpload
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltWorker
internal class FolderUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(createForegroundInfo())

            val storageIdString = inputData.getString(KEY_STORAGE_ID) ?: return@withContext Result.failure()
            val fileObjectIdString = inputData.getString(KEY_FILE_OBJECT_ID) ?: return@withContext Result.failure()
            val folderName = inputData.getString(KEY_FOLDER_NAME) ?: return@withContext Result.failure()
            val uriDataListJson = inputData.getString(KEY_URI_DATA_LIST) ?: return@withContext Result.failure()

            val storageId = Json.decodeFromString<StorageId>(storageIdString)
            val fileObjectId = Json.decodeFromString<FileObjectId>(fileObjectIdString)
            val uriDataList = Json.decodeFromString<List<UriData>>(uriDataListJson)
            val repository = storageRepository.getFileRepository(storageId)
                ?: return@withContext Result.failure()

            val filesToUpload = uriDataList.mapNotNull { uriData ->
                val uri = android.net.Uri.parse(uriData.uri)
                context.contentResolver.openInputStream(uri)?.let { inputStream ->
                    FileToUpload(
                        relativePath = uriData.relativePath,
                        inputStream = inputStream,
                    )
                }
            }

            repository.uploadFolder(fileObjectId, folderName, filesToUpload)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
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

    @Serializable
    data class UriData(
        val uri: String,
        val relativePath: String,
    )

    companion object {
        private const val CHANNEL_ID = "folder_upload_channel"
        private const val NOTIFICATION_ID = 2

        const val KEY_STORAGE_ID = "storage_id"
        const val KEY_FILE_OBJECT_ID = "file_object_id"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_URI_DATA_LIST = "uri_data_list"
    }
}
