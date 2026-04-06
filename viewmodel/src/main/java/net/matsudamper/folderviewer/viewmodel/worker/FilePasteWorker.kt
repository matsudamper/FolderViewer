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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.matsudamper.folderviewer.repository.ClipboardRepository
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.PasteJobRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.common.FileObjectId

@HiltWorker
internal class FilePasteWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageRepository: StorageRepository,
    private val pasteJobRepository: PasteJobRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getLong(KEY_PASTE_JOB_ID, -1L)
        if (jobId == -1L) return@withContext Result.failure()

        val job = pasteJobRepository.getJobById(jobId) ?: return@withContext Result.failure()
        val notificationId = PASTE_NOTIFICATION_BASE_ID + jobId.toInt()

        try {
            setForeground(createForegroundInfo(notificationId, 0, job.totalFiles, null))

            val files = pasteJobRepository.getFiles(jobId)
            val pendingFiles = files.filter { !it.completed }

            if (pendingFiles.isEmpty()) {
                pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.COMPLETED)
                return@withContext Result.success()
            }

            val sourceStorageId = pendingFiles.first().sourceFileId.storageId
            val sourceRepo = storageRepository.getFileRepository(sourceStorageId)
                ?: return@withContext Result.failure()
            val destRepo = storageRepository.getFileRepository(job.destinationFileObjectId.storageId)
                ?: return@withContext Result.failure()

            var completedFiles = files.count { it.completed }
            var completedBytes = files.filter { it.completed }.sumOf { it.fileSize }

            val directoryCache = mutableMapOf<String, FileObjectId>()

            for (file in pendingFiles) {
                if (isStopped) {
                    pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.PAUSED, workerId = null)
                    return@withContext Result.success()
                }

                pasteJobRepository.updateProgress(
                    jobId = jobId,
                    progress = PasteJobRepository.ProgressUpdate(
                        completedFiles = completedFiles,
                        completedBytes = completedBytes,
                        currentFileName = file.fileName,
                        currentFileBytes = 0L,
                        currentFileTotalBytes = file.fileSize,
                    ),
                )
                updateNotification(notificationId, completedFiles, job.totalFiles, file.fileName)

                val progressFlow = MutableStateFlow(0L)
                val progressJob = launch {
                    progressFlow.collectLatest { currentBytes ->
                        pasteJobRepository.updateProgress(
                            jobId = jobId,
                            progress = PasteJobRepository.ProgressUpdate(
                                completedFiles = completedFiles,
                                completedBytes = completedBytes,
                                currentFileName = file.fileName,
                                currentFileBytes = currentBytes,
                                currentFileTotalBytes = file.fileSize,
                            ),
                        )
                    }
                }

                try {
                    if (file.isDirectory) {
                        val destPath = if (file.destinationRelativePath.isEmpty()) {
                            file.fileName
                        } else {
                            "${file.destinationRelativePath}/${file.fileName}"
                        }
                        ensureDirectory(
                            path = destPath,
                            destRepo = destRepo,
                            rootId = job.destinationFileObjectId,
                            cache = directoryCache,
                        )
                    } else {
                        val destDirId = ensureDirectory(
                            path = file.destinationRelativePath,
                            destRepo = destRepo,
                            rootId = job.destinationFileObjectId,
                            cache = directoryCache,
                        )
                        sourceRepo.getFileContent(file.sourceFileId).use { inputStream ->
                            destRepo.uploadFile(
                                id = destDirId,
                                fileName = file.fileName,
                                inputStream = inputStream,
                                size = file.fileSize,
                                onRead = progressFlow,
                            )
                        }
                        // TODO: ベリファイ（uploadFileの戻り値変更後にファイルサイズ検証を追加）
                    }
                } finally {
                    progressJob.cancel()
                }

                pasteJobRepository.markFileCompleted(file.id)

                if (job.mode == ClipboardRepository.ClipboardMode.Cut && !file.deleted) {
                    if (file.isDirectory) {
                        sourceRepo.deleteDirectory(file.sourceFileId)
                    } else {
                        sourceRepo.deleteFile(file.sourceFileId)
                    }
                    pasteJobRepository.markFileDeleted(file.id)
                }

                completedFiles++
                completedBytes += file.fileSize

                pasteJobRepository.updateProgress(
                    jobId = jobId,
                    progress = PasteJobRepository.ProgressUpdate(
                        completedFiles = completedFiles,
                        completedBytes = completedBytes,
                        currentFileName = null,
                        currentFileBytes = 0L,
                        currentFileTotalBytes = 0L,
                    ),
                )
                updateNotification(notificationId, completedFiles, job.totalFiles, null)
            }

            if (job.mode == ClipboardRepository.ClipboardMode.Cut) {
                deleteEmptySourceDirectories(files, sourceRepo)
            }

            pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.COMPLETED)
            Result.success()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            pasteJobRepository.updateError(
                jobId = jobId,
                errorMessage = e.message,
                errorCause = e.cause?.toString(),
            )
            Result.failure()
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
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private suspend fun deleteEmptySourceDirectories(
        files: List<PasteJobRepository.PasteFile>,
        sourceRepo: FileRepository,
    ) {
        val ancestorDirPaths = buildList {
            val seen = mutableSetOf<String>()
            for (file in files) {
                var dir = file.sourceFileId.id.substringBeforeLast("/", "")
                while (dir.isNotEmpty() && seen.add(dir)) {
                    add(Pair(dir, file.sourceFileId.storageId))
                    dir = dir.substringBeforeLast("/", "")
                }
            }
        }.sortedByDescending { it.first.count { c -> c == '/' } }

        for ((dirPath, storageId) in ancestorDirPaths) {
            val dirId = FileObjectId.Item(storageId = storageId, id = dirPath)
            deleteDirectoryIfEmpty(dirId, sourceRepo)
        }
    }

    private suspend fun deleteDirectoryIfEmpty(
        dirId: FileObjectId.Item,
        sourceRepo: FileRepository,
    ) {
        val children = runCatching { sourceRepo.getFiles(dirId) }.getOrNull() ?: return

        for (child in children) {
            if (child.isDirectory) {
                deleteDirectoryIfEmpty(child.id, sourceRepo)
            }
        }

        val remaining = runCatching { sourceRepo.getFiles(dirId) }.getOrNull() ?: return
        if (remaining.isEmpty()) {
            runCatching { sourceRepo.deleteDirectory(dirId) }
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

    companion object {
        private const val CHANNEL_ID = "paste_channel"
        private const val PASTE_NOTIFICATION_BASE_ID = 1000

        const val TAG_PASTE = "paste"
        const val KEY_PASTE_JOB_ID = "paste_job_id"
    }
}
