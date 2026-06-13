package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.OperationRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltWorker
internal class FilePasteWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
    private val pasteJobRepository: PasteJobRepository,
    private val operationNotificationIntentFactory: OperationNotificationIntentFactory,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getLong(KEY_PASTE_JOB_ID, -1L)
        if (jobId == -1L) return@withContext Result.failure()
        val meta = pasteJobRepository.getJobMeta(jobId) ?: return@withContext Result.failure()

        try {
            pasteJobRepository.resetRunningFiles(jobId)
            pasteJobRepository.updateStatus(
                jobId = jobId,
                status = OperationRepository.OperationStatus.RUNNING,
                workerId = id.toString(),
            )
            executeJob(meta)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                pasteJobRepository.pauseJob(jobId)
            }
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            pasteJobRepository.updateError(
                jobId = jobId,
                errorMessage = e.message,
                errorCause = e.cause?.toString(),
            )
            notifyFailed(jobId, e.message ?: e.toString())
            Result.failure()
        }
    }

    private suspend fun executeJob(meta: PasteJobRepository.PasteJobMeta): Result {
        val jobId = meta.id
        val notificationId = PASTE_NOTIFICATION_BASE_ID + jobId.toInt()
        val allFiles = pasteJobRepository.getFiles(jobId)
        val totalFiles = allFiles.count { !it.isDirectory }
        setForeground(createForegroundInfo(notificationId, 0, totalFiles, null))

        val pendingFiles = pasteJobRepository.getPendingFiles(jobId)
        if (pendingFiles.isEmpty()) {
            val sourceRepo = allFiles.firstOrNull()?.let { file ->
                storageRepository.getFileRepository(file.sourceFileId.storageId)
            }
            return finishJob(meta, sourceRepo = sourceRepo, totalFiles = totalFiles)
        }

        val sourceStorageId = pendingFiles.first().sourceFileId.storageId
        val sourceRepo = storageRepository.getFileRepository(sourceStorageId) ?: run {
            pasteJobRepository.updateError(jobId, "ソースストレージが見つかりません: $sourceStorageId", null)
            return Result.failure()
        }
        val destStorageId = meta.destinationFileObjectId.storageId
        val destRepo = storageRepository.getFileRepository(destStorageId) ?: run {
            pasteJobRepository.updateError(jobId, "宛先ストレージが見つかりません: $destStorageId", null)
            return Result.failure()
        }

        val jobContext = JobContext(
            meta = meta,
            sourceRepo = sourceRepo,
            destRepo = destRepo,
            notificationId = notificationId,
            totalFiles = totalFiles,
            completedCount = allFiles.count {
                !it.isDirectory && it.status == OperationRepository.FileStatus.COMPLETED
            },
        )

        if (!processDirectories(jobContext, pendingFiles.filter { it.isDirectory })) {
            return Result.success()
        }
        if (!processFileEntries(jobContext, pendingFiles.filter { !it.isDirectory })) {
            return Result.success()
        }
        return finishJob(meta, sourceRepo, totalFiles)
    }

    private suspend fun finishJob(
        meta: PasteJobRepository.PasteJobMeta,
        sourceRepo: FileRepository?,
        totalFiles: Int,
    ): Result {
        val jobId = meta.id
        val pendingDuplicateCount = pasteJobRepository.countPendingDuplicates(jobId)
        if (pendingDuplicateCount > 0) {
            val unresolvedCount = pasteJobRepository.countUnresolvedDuplicates(jobId)
            pasteJobRepository.updateStatus(
                jobId = jobId,
                status = OperationRepository.OperationStatus.WAITING_RESOLUTION,
                workerId = null,
            )
            OperationResultNotification.notify(
                context = context,
                notificationId = PASTE_RESULT_NOTIFICATION_BASE_ID + jobId.toInt(),
                content = OperationResultNotification.Content(
                    title = "ファイルペーストの操作が必要です",
                    text = if (unresolvedCount > 0) {
                        "重複ファイルが $unresolvedCount 件あります"
                    } else {
                        "重複ファイル $pendingDuplicateCount 件の解決を適用してください"
                    },
                    smallIcon = android.R.drawable.stat_notify_error,
                ),
                contentIntent = operationNotificationIntentFactory.createPasteDetailIntent(jobId),
            )
            return Result.success()
        }

        if (meta.mode == ClipboardRepository.ClipboardMode.Cut && sourceRepo != null) {
            deleteSourceDirectories(jobId, sourceRepo)
        }

        val failedCount = pasteJobRepository.countFailedFiles(jobId)
        if (failedCount > 0) {
            pasteJobRepository.updateStatus(jobId, OperationRepository.OperationStatus.FAILED)
            notifyFailed(jobId, "$failedCount 件のファイルが失敗しました")
        } else {
            pasteJobRepository.updateStatus(jobId, OperationRepository.OperationStatus.COMPLETED)
            notifyCompleted(jobId, totalFiles)
        }
        return Result.success()
    }

    private fun notifyCompleted(jobId: Long, totalFiles: Int) {
        OperationResultNotification.notify(
            context = context,
            notificationId = PASTE_RESULT_NOTIFICATION_BASE_ID + jobId.toInt(),
            content = OperationResultNotification.Content(
                title = "ファイルペーストが完了しました",
                text = "$totalFiles ファイル",
                smallIcon = android.R.drawable.stat_sys_upload_done,
            ),
            contentIntent = operationNotificationIntentFactory.createPasteDetailIntent(jobId),
        )
    }

    private fun notifyFailed(jobId: Long, text: String) {
        OperationResultNotification.notify(
            context = context,
            notificationId = PASTE_RESULT_NOTIFICATION_BASE_ID + jobId.toInt(),
            content = OperationResultNotification.Content(
                title = "ファイルペーストに失敗しました",
                text = text,
                smallIcon = android.R.drawable.stat_notify_error,
            ),
            contentIntent = operationNotificationIntentFactory.createPasteDetailIntent(jobId),
        )
    }

    private suspend fun processDirectories(
        jobContext: JobContext,
        directories: List<PasteJobRepository.PasteFile>,
    ): Boolean {
        for (dir in directories) {
            if (isStopped) {
                pasteJobRepository.pauseJob(jobContext.meta.id)
                return false
            }
            try {
                ensureDirectory(
                    path = dir.displayPath(),
                    destRepo = jobContext.destRepo,
                    rootId = jobContext.meta.destinationFileObjectId,
                    cache = jobContext.directoryCache,
                )
                pasteJobRepository.markFileCompleted(dir.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                pasteJobRepository.markFileFailed(dir.id, e.message ?: e.toString())
                throw e
            }
        }
        return true
    }

    private suspend fun processFileEntries(
        jobContext: JobContext,
        files: List<PasteJobRepository.PasteFile>,
    ): Boolean {
        files.forEachIndexed { index, file ->
            if (isStopped) {
                pasteJobRepository.pauseJob(jobContext.meta.id)
                return false
            }
            if (index == 0) {
                pasteJobRepository.finishFileAndStartNext(finish = null, nextFileId = file.id)
            }
            val finish = processFile(jobContext, file)
            pasteJobRepository.finishFileAndStartNext(
                finish = finish,
                nextFileId = files.getOrNull(index + 1)?.id,
            )
            if (finish is PasteJobRepository.FileFinish.Completed) {
                jobContext.completedCount++
            }
            updateNotification(
                notificationId = jobContext.notificationId,
                completedFiles = jobContext.completedCount,
                totalFiles = jobContext.totalFiles,
                currentFileName = file.displayPath(),
            )
        }
        return true
    }

    private suspend fun processFile(
        jobContext: JobContext,
        file: PasteJobRepository.PasteFile,
    ): PasteJobRepository.FileFinish {
        return when {
            file.resolution == PasteJobRepository.DuplicateResolution.KEEP_DESTINATION -> {
                processKeepDestination(jobContext, file)
            }
            file.resolution != null && file.sourceFileId == file.destinationFileId -> {
                PasteJobRepository.FileFinish.Completed(file.id)
            }
            else -> copyFile(jobContext, file)
        }
    }

    private suspend fun processKeepDestination(
        jobContext: JobContext,
        file: PasteJobRepository.PasteFile,
    ): PasteJobRepository.FileFinish {
        if (jobContext.meta.mode == ClipboardRepository.ClipboardMode.Cut &&
            !file.sourceDeleted &&
            file.sourceFileId != file.destinationFileId
        ) {
            try {
                jobContext.sourceRepo.deleteFile(file.sourceFileId)
                pasteJobRepository.markFileSourceDeleted(file.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return PasteJobRepository.FileFinish.Failed(file.id, e.message ?: e.toString())
            }
        }
        return PasteJobRepository.FileFinish.Completed(file.id)
    }

    private suspend fun copyFile(
        jobContext: JobContext,
        file: PasteJobRepository.PasteFile,
    ): PasteJobRepository.FileFinish = coroutineScope {
        val progressFlow = MutableStateFlow(0L)
        val progressJob = launch {
            progressFlow.collectLatest { currentBytes ->
                pasteJobRepository.updateFileTransferred(file.id, currentBytes)
            }
        }
        try {
            val destDirId = ensureDirectory(
                path = file.relativePath,
                destRepo = jobContext.destRepo,
                rootId = jobContext.meta.destinationFileObjectId,
                cache = jobContext.directoryCache,
            )

            if (file.resolution == null) {
                if (jobContext.uploadedNames[destDirId]?.contains(file.fileName) == true) {
                    jobContext.destinationFilesCache.remove(destDirId)
                    jobContext.uploadedNames.remove(destDirId)
                }
                val existing = getDestinationFiles(jobContext, destDirId)[file.fileName]
                if (existing != null) {
                    return@coroutineScope PasteJobRepository.FileFinish.Duplicated(
                        fileId = file.id,
                        destinationFileId = existing.id,
                        destinationFileSize = existing.size,
                    )
                }
            }

            jobContext.sourceRepo.getFileContent(file.sourceFileId).use { inputStream ->
                jobContext.destRepo.uploadFile(
                    id = destDirId,
                    fileName = file.fileName,
                    inputStream = inputStream,
                    size = file.fileSize,
                    onRead = progressFlow,
                    overwrite = file.resolution == PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE,
                )
            }
            jobContext.uploadedNames.getOrPut(destDirId) { mutableSetOf() }.add(file.fileName)

            if (jobContext.meta.mode == ClipboardRepository.ClipboardMode.Cut && !file.sourceDeleted) {
                try {
                    jobContext.sourceRepo.deleteFile(file.sourceFileId)
                    pasteJobRepository.markFileSourceDeleted(file.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                    return@coroutineScope PasteJobRepository.FileFinish.Failed(file.id, e.message ?: e.toString())
                }
            }
            PasteJobRepository.FileFinish.Completed(file.id)
        } finally {
            progressJob.cancel()
        }
    }

    private suspend fun getDestinationFiles(
        jobContext: JobContext,
        destDirId: FileObjectId,
    ): Map<String, FileItem> {
        return jobContext.destinationFilesCache.getOrPut(destDirId) {
            jobContext.destRepo.getFiles(destDirId)
                .filterNot { it.isDirectory }
                .associateBy { it.displayPath }
        }
    }

    private suspend fun deleteSourceDirectories(jobId: Long, sourceRepo: FileRepository) {
        pasteJobRepository.getFiles(jobId)
            .filter { it.isDirectory && !it.sourceDeleted }
            .distinctBy { it.sourceFileId }
            .sortedByDescending { file -> file.displayPath().count { it == '/' } }
            .forEach { file ->
                runCatching {
                    sourceRepo.deleteDirectory(file.sourceFileId)
                    pasteJobRepository.markFileSourceDeleted(file.id)
                }.onFailure { throwable ->
                    throwable.printStackTrace()
                    pasteJobRepository.markFileFailed(file.id, throwable.message ?: throwable.toString())
                }
            }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val jobId = inputData.getLong(KEY_PASTE_JOB_ID, -1L)
        val notificationId = PASTE_NOTIFICATION_BASE_ID + jobId.toInt()
        return createForegroundInfo(notificationId, 0, 0, null)
    }

    private fun createForegroundInfo(
        notificationId: Int,
        completedFiles: Int,
        totalFiles: Int,
        currentFileName: String?,
    ): ForegroundInfo {
        createNotificationChannel()

        val title = "ファイルペースト中"
        val text = if (currentFileName != null) {
            "$currentFileName ($completedFiles/$totalFiles)"
        } else {
            "$completedFiles/$totalFiles ファイル"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(totalFiles, completedFiles, totalFiles == 0)
            .setOngoing(true)
            .setContentIntent(createContentIntent())
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
        val title = "ファイルペースト中"
        val text = if (currentFileName != null) {
            "$currentFileName ($completedFiles/$totalFiles)"
        } else {
            "$completedFiles/$totalFiles ファイル完了"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(totalFiles, completedFiles, false)
            .setOngoing(true)
            .setContentIntent(createContentIntent())
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun createContentIntent(): PendingIntent {
        val jobId = inputData.getLong(KEY_PASTE_JOB_ID, -1L)
        return operationNotificationIntentFactory.createPasteDetailIntent(jobId)
    }

    private fun PasteJobRepository.PasteFile.displayPath(): String {
        return if (relativePath.isEmpty()) {
            fileName
        } else {
            "$relativePath/$fileName"
        }
    }

    private suspend fun ensureDirectory(
        path: String,
        destRepo: FileRepository,
        rootId: FileObjectId,
        cache: MutableMap<String, FileObjectId>,
    ): FileObjectId {
        if (path.isEmpty()) return rootId
        cache[path]?.let { return it }

        val parentPath = path.substringBeforeLast("/", "")
        val parentId = ensureDirectory(parentPath, destRepo, rootId, cache)
        val dirName = path.substringAfterLast("/")
        val newDirId = destRepo.createDirectory(parentId, dirName)
        cache[path] = newDirId
        return newDirId
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ファイルペースト",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ファイルのペースト状態を表示します"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private class JobContext(
        val meta: PasteJobRepository.PasteJobMeta,
        val sourceRepo: FileRepository,
        val destRepo: FileRepository,
        val notificationId: Int,
        val totalFiles: Int,
        var completedCount: Int,
        val directoryCache: MutableMap<String, FileObjectId> = mutableMapOf(),
        val destinationFilesCache: MutableMap<FileObjectId, Map<String, FileItem>> = mutableMapOf(),
        val uploadedNames: MutableMap<FileObjectId, MutableSet<String>> = mutableMapOf(),
    )

    companion object {
        private const val CHANNEL_ID = "paste_channel"
        private const val PASTE_NOTIFICATION_BASE_ID = 1000
        private const val PASTE_RESULT_NOTIFICATION_BASE_ID = 5000

        const val TAG_PASTE = "paste"
        const val KEY_PASTE_JOB_ID = "paste_job_id"
    }
}
