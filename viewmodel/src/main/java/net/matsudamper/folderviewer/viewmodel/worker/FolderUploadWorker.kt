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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
            val storageIdString = inputData.getString(KEY_STORAGE_ID) ?: return@withContext Result.failure()
            val fileObjectIdString = inputData.getString(KEY_FILE_OBJECT_ID) ?: return@withContext Result.failure()
            val folderName = inputData.getString(KEY_FOLDER_NAME) ?: return@withContext Result.failure()
            val uriDataListJson = inputData.getString(KEY_URI_DATA_LIST) ?: return@withContext Result.failure()

            setProgress(
                androidx.work.Data.Builder()
                    .putString(KEY_STORAGE_ID, storageIdString)
                    .putString(KEY_FILE_OBJECT_ID, fileObjectIdString)
                    .putString(KEY_FOLDER_NAME, folderName)
                    .build(),
            )

            setForeground(createForegroundInfo())

            val storageId = Json.decodeFromString<StorageId>(storageIdString)
            val fileObjectId = Json.decodeFromString<FileObjectId>(fileObjectIdString)
            val uriDataList = Json.decodeFromString<List<UriData>>(uriDataListJson)
            val repository = storageRepository.getFileRepository(storageId)
                ?: return@withContext Result.failure()

            val filesToUpload = getFilesToUpload(uriDataList)
            val totalSize = filesToUpload.fold<FileToUpload, Long?>(0L) { acc, file ->
                val size = file.size
                if (acc != null && size != null) acc + size else null
            }

            val progressFlow = MutableStateFlow(0L)
            val progressJob = launch {
                progressFlow.collectLatest { uploadedBytes ->
                    val builder = androidx.work.Data.Builder()
                        .putString(KEY_STORAGE_ID, storageIdString)
                        .putString(KEY_FILE_OBJECT_ID, fileObjectIdString)
                        .putString(KEY_FOLDER_NAME, folderName)
                        .putLong("CurrentBytes", uploadedBytes)
                    if (totalSize != null) {
                        builder.putLong("TotalBytes", totalSize)
                    }
                    setProgress(builder.build())
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
        return uriDataList.mapNotNull { uriData ->
            val uri = android.net.Uri.parse(uriData.uri)

            val fileSize = getFileSize(uri)

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                FileToUpload(
                    relativePath = uriData.relativePath,
                    inputStream = inputStream,
                    size = fileSize,
                )
            } else {
                null
            }
        }
    }

    @Serializable
    data class UriData(
        val uri: String,
        val relativePath: String,
    )

    companion object {
        private const val CHANNEL_ID = "folder_upload_channel"
        private const val NOTIFICATION_ID = 2

        const val TAG_UPLOAD = "upload"
        const val KEY_STORAGE_ID = "storage_id"
        const val KEY_FILE_OBJECT_ID = "file_object_id"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_URI_DATA_LIST = "uri_data_list"
    }
}
