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
            val pendingFiles = files.filter {
                !it.completed && !(it.isDuplicate && it.resolution == null)
            }

            if (pendingFiles.isEmpty()) {
                pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.COMPLETED)
                return@withContext Result.success()
            }

            val sourceStorageId = pendingFiles.first().sourceFileId.storageId
            val sourceRepo = storageRepository.getFileRepository(sourceStorageId)
                ?: return@withContext Result.failure()
            val destRepo = storageRepository.getFileRepository(job.destinationFileObjectId.storageId)
                ?: return@withContext Result.failure()

            val directoryCache = mutableMapOf<String, FileObjectId>()

            // ディレクトリエントリを先に処理（ファイルとしてカウントしない）
            val pendingDirectories = pendingFiles.filter { it.isDirectory }
            val pendingFileEntries = pendingFiles.filter { !it.isDirectory }

            for (dir in pendingDirectories) {
                if (isStopped) {
                    pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.PAUSED, workerId = null)
                    return@withContext Result.success()
                }

                val destPath = if (dir.destinationRelativePath.isEmpty()) {
                    dir.fileName
                } else {
                    "${dir.destinationRelativePath}/${dir.fileName}"
                }
                try {
                    ensureDirectory(
                        path = destPath,
                        destRepo = destRepo,
                        rootId = job.destinationFileObjectId,
                        cache = directoryCache,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                pasteJobRepository.markFileCompleted(dir.id)

                if (job.mode == ClipboardRepository.ClipboardMode.Cut && !dir.deleted) {
                    try {
                        sourceRepo.deleteDirectory(dir.sourceFileId)
                        pasteJobRepository.markFileDeleted(dir.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        pasteJobRepository.markFileFailed(dir.id, e.message ?: e.toString())
                    }
                }
            }

            // ファイルエントリを処理
            var completedFiles = files.count { it.completed && !it.isDirectory }
            var completedBytes = files.filter { it.completed && !it.isDirectory }.sumOf { it.fileSize }

            for (file in pendingFileEntries) {
                if (isStopped) {
                    pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.PAUSED, workerId = null)
                    return@withContext Result.success()
                }

                if (file.isDuplicate && file.resolution == PasteJobRepository.DuplicateResolution.KEEP_DESTINATION) {
                    pasteJobRepository.markFileCompleted(file.id)
                    if (job.mode == ClipboardRepository.ClipboardMode.Cut && !file.deleted) {
                        try {
                            sourceRepo.deleteFile(file.sourceFileId)
                            pasteJobRepository.markFileDeleted(file.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            pasteJobRepository.markFileFailed(file.id, e.message ?: e.toString())
                        }
                    }
                    completedFiles++
                    completedBytes += file.fileSize
                } else if (!file.isDuplicate || file.resolution == PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE) {
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

                    var detectedDuplicate = false
                    try {
                        val destDirId = ensureDirectory(
                            path = file.destinationRelativePath,
                            destRepo = destRepo,
                            rootId = job.destinationFileObjectId,
                            cache = directoryCache,
                        )

                        if (!file.isDuplicate) {
                            val destFiles = destRepo.getFiles(destDirId)
                            val existing = destFiles.find { !it.isDirectory && it.displayPath == file.fileName }
                            if (existing != null) {
                                pasteJobRepository.markFileDuplicate(
                                    fileId = file.id,
                                    destinationFileId = existing.id,
                                    destinationFileSize = existing.size,
                                )
                                detectedDuplicate = true
                            }
                        }

                        if (!detectedDuplicate) {
                            sourceRepo.getFileContent(file.sourceFileId).use { inputStream ->
                                destRepo.uploadFile(
                                    id = destDirId,
                                    fileName = file.fileName,
                                    inputStream = inputStream,
                                    size = file.fileSize,
                                    onRead = progressFlow,
                                    overwrite = file.isDuplicate &&
                                        file.resolution == PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE,
                                )
                            }
                        }
                    } finally {
                        progressJob.cancel()
                    }

                    if (!detectedDuplicate) {
                        pasteJobRepository.markFileCompleted(file.id)

                        if (job.mode == ClipboardRepository.ClipboardMode.Cut && !file.deleted) {
                            try {
                                sourceRepo.deleteFile(file.sourceFileId)
                                pasteJobRepository.markFileDeleted(file.id)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                pasteJobRepository.markFileFailed(file.id, e.message ?: e.toString())
                            }
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
                }
            }

            val unresolvedCount = pasteJobRepository.countUnresolvedDuplicates(jobId)
            if (unresolvedCount > 0) {
                pasteJobRepository.updateDuplicateCount(jobId, unresolvedCount)
                pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.WAITING_RESOLUTION, workerId = null)
                return@withContext Result.success()
            }

            if (job.mode == ClipboardRepository.ClipboardMode.Cut) {
                deleteEmptySourceDirectories(files, sourceRepo)
            }

            pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.COMPLETED)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
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
                // isDirectory=true はディレクトリ自身を起点にする
                // isDirectory=false はファイルの親ディレクトリを起点にする
                val startPath = if (file.isDirectory) {
                    file.sourceFileId.id
                } else {
                    file.sourceFileId.id.substringBeforeLast("/", "")
                }
                var dir = startPath
                while (dir.isNotEmpty() && seen.add(dir)) {
                    add(Pair(dir, file.sourceFileId.storageId))
                    dir = dir.substringBeforeLast("/", "")
                }
            }
        }.sortedByDescending { it.first.count { c -> c == '/' } }

        for ((dirPath, storageId) in ancestorDirPaths) {
            val dirId = FileObjectId.Item(storageId = storageId, id = dirPath)
            // 非空ディレクトリの削除は実装側で失敗するため安全
            runCatching { sourceRepo.deleteDirectory(dirId) }
                .onFailure { it.printStackTrace() }
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
