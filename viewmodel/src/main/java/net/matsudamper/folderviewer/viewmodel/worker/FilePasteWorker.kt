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
            pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.RUNNING, workerId = id.toString())
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
                ?: run {
                    pasteJobRepository.updateError(jobId, "ソースストレージが見つかりません: $sourceStorageId", null)
                    return@withContext Result.failure()
                }
            val destRepo = storageRepository.getFileRepository(job.destinationFileObjectId.storageId)
                ?: run {
                    pasteJobRepository.updateError(jobId, "宛先ストレージが見つかりません: ${job.destinationFileObjectId.storageId}", null)
                    return@withContext Result.failure()
                }

            val directoryCache = mutableMapOf<String, FileObjectId>()

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
                    pasteJobRepository.markFileCompleted(dir.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    pasteJobRepository.markFileFailed(jobId, dir.id, e.message ?: e.toString())
                    throw e
                }
            }

            var completedFiles = files.count { it.completed && !it.isDirectory }
            var completedBytes = files.filter { it.completed && !it.isDirectory }.sumOf { it.fileSize }

            for (file in pendingFileEntries) {
                if (isStopped) {
                    pasteJobRepository.updateStatus(jobId, PasteJobRepository.PasteJobStatus.PAUSED, workerId = null)
                    return@withContext Result.success()
                }

                val currentFileName = file.displayPath()
                if (file.isDuplicate && file.resolution == PasteJobRepository.DuplicateResolution.KEEP_DESTINATION) {
                    pasteJobRepository.markFileCompleted(file.id)
                    if (job.mode == ClipboardRepository.ClipboardMode.Cut && !file.deleted &&
                        file.sourceFileId != file.destinationFileId
                    ) {
                        try {
                            sourceRepo.deleteFile(file.sourceFileId)
                            pasteJobRepository.markFileDeleted(file.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            pasteJobRepository.markFileFailed(jobId, file.id, e.message ?: e.toString())
                        }
                    }
                    completedFiles++
                    completedBytes += file.fileSize
                    pasteJobRepository.updateProgress(
                        jobId = jobId,
                        progress = PasteJobRepository.ProgressUpdate(
                            completedFiles = completedFiles,
                            completedBytes = completedBytes,
                            currentFileName = currentFileName,
                            currentFileBytes = 0L,
                            currentFileTotalBytes = 0L,
                        ),
                    )
                    updateNotification(notificationId, completedFiles, job.totalFiles, currentFileName)
                } else if (!file.isDuplicate || file.resolution == PasteJobRepository.DuplicateResolution.OVERWRITE_WITH_SOURCE) {
                    if (file.isDuplicate && file.sourceFileId == file.destinationFileId) {
                        pasteJobRepository.markFileCompleted(file.id)
                        completedFiles++
                        completedBytes += file.fileSize
                        pasteJobRepository.updateProgress(
                            jobId = jobId,
                            progress = PasteJobRepository.ProgressUpdate(
                                completedFiles = completedFiles,
                                completedBytes = completedBytes,
                                currentFileName = currentFileName,
                                currentFileBytes = 0L,
                                currentFileTotalBytes = 0L,
                            ),
                        )
                        updateNotification(notificationId, completedFiles, job.totalFiles, currentFileName)
                        continue
                    }
                    pasteJobRepository.updateProgress(
                        jobId = jobId,
                        progress = PasteJobRepository.ProgressUpdate(
                            completedFiles = completedFiles,
                            completedBytes = completedBytes,
                            currentFileName = currentFileName,
                            currentFileBytes = 0L,
                            currentFileTotalBytes = file.fileSize,
                        ),
                    )
                    updateNotification(notificationId, completedFiles, job.totalFiles, currentFileName)

                    val progressFlow = MutableStateFlow(0L)
                    val progressJob = launch {
                        progressFlow.collectLatest { currentBytes ->
                            pasteJobRepository.updateProgress(
                                jobId = jobId,
                                progress = PasteJobRepository.ProgressUpdate(
                                    completedFiles = completedFiles,
                                    completedBytes = completedBytes,
                                    currentFileName = currentFileName,
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
                                pasteJobRepository.markFileFailed(jobId, file.id, e.message ?: e.toString())
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
                deleteSourceDirectories(files, sourceRepo)
            }

            val finalStatus = if (pasteJobRepository.countFailedFiles(jobId) > 0) {
                PasteJobRepository.PasteJobStatus.FAILED
            } else {
                PasteJobRepository.PasteJobStatus.COMPLETED
            }
            pasteJobRepository.updateStatus(jobId, finalStatus)
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

    private fun PasteJobRepository.PasteFile.displayPath(): String {
        return if (destinationRelativePath.isEmpty()) {
            fileName
        } else {
            "$destinationRelativePath/$fileName"
        }
    }


    private suspend fun deleteSourceDirectories(
        files: List<PasteJobRepository.PasteFile>,
        sourceRepo: FileRepository,
    ) {
        files
            .filter { it.isDirectory && !it.deleted }
            .distinctBy { it.sourceFileId }
            .sortedByDescending { file ->
                val path = if (file.destinationRelativePath.isEmpty()) {
                    file.fileName
                } else {
                    "${file.destinationRelativePath}/${file.fileName}"
                }
                path.count { it == '/' }
            }
            .forEach { file ->
                runCatching {
                    sourceRepo.deleteDirectory(file.sourceFileId)
                    pasteJobRepository.markFileDeleted(file.id)
                }.onFailure { throwable ->
                    throwable.printStackTrace()
                    pasteJobRepository.markFileFailed(file.jobId, file.id, throwable.message ?: throwable.toString())
                }
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
