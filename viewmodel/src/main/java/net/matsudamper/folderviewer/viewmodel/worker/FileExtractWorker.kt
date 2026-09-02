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
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.viewmodel.util.ExtractMediaScanner
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputNameValidator
import net.matsudamper.folderviewer.viewmodel.util.TarGzFileUtil
import net.matsudamper.folderviewer.viewmodel.util.ZipFileUtil

@HiltWorker
internal class FileExtractWorker @AssistedInject constructor(
    @Assisted private val workerContext: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
    private val extractJobRepository: ExtractJobRepository,
    private val operationNotificationIntentFactory: OperationNotificationIntentFactory,
) : CoroutineWorker(workerContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val operationId = inputData.getLong(KEY_EXTRACT_OPERATION_ID, -1L)
        if (operationId == -1L) return@withContext Result.failure()
        val meta = extractJobRepository.getJobMeta(operationId) ?: return@withContext Result.failure()

        try {
            extractJobRepository.updateStatus(
                operationId = operationId,
                status = OperationRepository.OperationStatus.RUNNING,
                workerId = id.toString(),
            )
            executeJob(meta)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                extractJobRepository.updateStatus(
                    operationId = operationId,
                    status = OperationRepository.OperationStatus.CANCELLED,
                )
            }
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            extractJobRepository.updateError(
                operationId = operationId,
                errorMessage = e.message,
                errorCause = e.cause?.toString(),
            )
            notifyFailed(operationId, e.message ?: e.toString())
            Result.failure()
        }
    }

    private suspend fun executeJob(meta: ExtractJobRepository.ExtractJobMeta): Result {
        val notificationId = EXTRACT_NOTIFICATION_BASE_ID + meta.id.toInt()
        setForeground(createForegroundInfo(notificationId, meta.sourceFileName))

        val extractResult = ExtractWorkerExecutor.run(
            meta = meta,
            storageRepository = storageRepository,
            appContext = workerContext,
        )
        return extractResult.fold(
            onSuccess = { outputFile ->
                extractJobRepository.completeJob(meta.id, outputFile.absolutePath)
                notifyCompleted(meta)
                Result.success()
            },
            onFailure = { error ->
                extractJobRepository.updateError(
                    operationId = meta.id,
                    errorMessage = error.message,
                    errorCause = error.cause?.toString(),
                )
                notifyFailed(meta.id, error.message ?: error.toString())
                Result.failure()
            },
        )
    }

    private fun notifyCompleted(meta: ExtractJobRepository.ExtractJobMeta) {
        OperationResultNotification.notify(
            context = workerContext,
            notificationId = EXTRACT_RESULT_NOTIFICATION_BASE_ID + meta.id.toInt(),
            content = OperationResultNotification.Content(
                title = "解凍が完了しました",
                text = "${meta.outputName}に展開しました",
                smallIcon = android.R.drawable.stat_sys_download_done,
            ),
            contentIntent = operationNotificationIntentFactory.createUploadProgressIntent(),
        )
    }

    private fun notifyFailed(operationId: Long, text: String) {
        OperationResultNotification.notify(
            context = workerContext,
            notificationId = EXTRACT_RESULT_NOTIFICATION_BASE_ID + operationId.toInt(),
            content = OperationResultNotification.Content(
                title = "解凍に失敗しました",
                text = text,
                smallIcon = android.R.drawable.stat_notify_error,
            ),
            contentIntent = operationNotificationIntentFactory.createUploadProgressIntent(),
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val operationId = inputData.getLong(KEY_EXTRACT_OPERATION_ID, -1L)
        val notificationId = EXTRACT_NOTIFICATION_BASE_ID + operationId.toInt()
        return createForegroundInfo(notificationId, null)
    }

    private fun createForegroundInfo(notificationId: Int, fileName: String?): ForegroundInfo {
        createNotificationChannel()
        val title = "解凍中"
        val text = fileName ?: "処理中"
        val notification = NotificationCompat.Builder(workerContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(operationNotificationIntentFactory.createUploadProgressIntent())
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "解凍",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ファイルの解凍状態を表示します"
        }
        val notificationManager = workerContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "extract_channel"
        private const val EXTRACT_NOTIFICATION_BASE_ID = 3000
        private const val EXTRACT_RESULT_NOTIFICATION_BASE_ID = 6000

        const val TAG_EXTRACT = "extract"
        const val KEY_EXTRACT_OPERATION_ID = "extract_operation_id"
    }
}

internal object ExtractWorkerExecutor {
    suspend fun run(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
        appContext: Context,
    ): Result<File> {
        return runCatching {
            val repository = storageRepository.getFileRepository(meta.sourceFileObjectId.storageId)
                ?: error("ストレージが見つかりません")
            val sourceFile = when (val uri = repository.getViewSourceUri(meta.sourceFileObjectId)) {
                is ViewSourceUri.LocalFile -> File(uri.path)
                is ViewSourceUri.RemoteUrl,
                is ViewSourceUri.StreamProvider,
                -> error("解凍はローカルストレージのみ対応しています")
            }
            when (meta.extractType) {
                ExtractJobRepository.ExtractType.Zip -> extractZip(sourceFile, meta, appContext)
                ExtractJobRepository.ExtractType.TarGz -> extractTarGz(sourceFile, meta, appContext)
                ExtractJobRepository.ExtractType.Zst,
                ExtractJobRepository.ExtractType.Xz,
                -> error("未対応の解凍形式です")
            }
        }
    }

    private fun extractZip(
        sourceFile: File,
        meta: ExtractJobRepository.ExtractJobMeta,
        appContext: Context,
    ): File {
        val extractDir = ExtractOutputNameValidator.resolveChildFile(meta.localFolderPath, meta.outputName)
            ?: error("無効なフォルダ名です")
        val extractedFiles = ZipFileUtil.extractZip(sourceFile, extractDir)
        ExtractMediaScanner.scanExtractedMediaFiles(appContext, extractedFiles)
        return extractDir
    }

    private fun extractTarGz(
        sourceFile: File,
        meta: ExtractJobRepository.ExtractJobMeta,
        appContext: Context,
    ): File {
        val extractDir = ExtractOutputNameValidator.resolveChildFile(meta.localFolderPath, meta.outputName)
            ?: error("無効なフォルダ名です")
        val extractedFiles = TarGzFileUtil.extractTarGz(sourceFile, extractDir)
        ExtractMediaScanner.scanExtractedMediaFiles(appContext, extractedFiles)
        return extractDir
    }
}
