package net.matsudamper.folderviewer.viewmodel.worker

import kotlinx.coroutines.runBlocking
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.viewmodel.util.ExtractProgressText

internal class ExtractProgressReporter(
    private val extractJobRepository: ExtractJobRepository,
    private val operationId: Long,
    private val onProgressChanged: (progressText: String?, progressRatio: Float?) -> Unit = { _, _ -> },
) {
    private var byteProgressFileId: Long? = null
    private var byteTotalBytes: Long = 0
    private var latestTransferredBytes: Long = 0
    private var lastPersistedBytes: Long = -1
    private var lastPersistedAtMs: Long = 0
    private var fileProgressIds: List<Long> = emptyList()
    private var nextFileIndex = 0

    suspend fun startByteProgress(totalBytes: Long, label: String) {
        clearProgress()
        byteTotalBytes = totalBytes
        latestTransferredBytes = 0
        lastPersistedBytes = -1
        lastPersistedAtMs = 0
        byteProgressFileId = extractJobRepository.initializeByteProgress(
            operationId = operationId,
            totalBytes = totalBytes,
            label = label,
        )
        notifyByteProgress(0L)
    }

    suspend fun startFileCountProgress(fileNames: List<String>) {
        clearProgress()
        fileProgressIds = extractJobRepository.initializeFileCountProgress(
            operationId = operationId,
            fileNames = fileNames,
        )
        nextFileIndex = 0
        fileProgressIds.firstOrNull()?.let { fileId ->
            extractJobRepository.startFileProgress(fileId)
        }
        notifyFileCountProgress()
    }

    fun updateBytes(transferredBytes: Long) {
        latestTransferredBytes = transferredBytes
        persistByteProgressIfNeeded(transferredBytes)
    }

    fun flushByteProgress() {
        persistByteProgressIfNeeded(latestTransferredBytes, force = true)
    }

    private fun persistByteProgressIfNeeded(transferredBytes: Long, force: Boolean = false) {
        val fileId = byteProgressFileId ?: return
        if (!force && !shouldPersistByteProgress(transferredBytes)) {
            return
        }
        if (!force && transferredBytes == lastPersistedBytes) {
            return
        }
        lastPersistedBytes = transferredBytes
        lastPersistedAtMs = System.currentTimeMillis()
        runBlocking {
            extractJobRepository.updateByteProgress(fileId, transferredBytes)
        }
        notifyByteProgress(transferredBytes)
    }

    private fun shouldPersistByteProgress(transferredBytes: Long): Boolean {
        if (byteTotalBytes > 0 && transferredBytes >= byteTotalBytes) {
            return true
        }
        if (lastPersistedBytes < 0) {
            return true
        }
        if (transferredBytes - lastPersistedBytes >= MIN_BYTE_UPDATE_INTERVAL) {
            return true
        }
        return System.currentTimeMillis() - lastPersistedAtMs >= MIN_TIME_UPDATE_INTERVAL_MS
    }

    fun onFileCompleted() {
        val fileId = fileProgressIds.getOrNull(nextFileIndex) ?: return
        runBlocking {
            extractJobRepository.completeFileProgress(fileId)
            nextFileIndex++
            fileProgressIds.getOrNull(nextFileIndex)?.let { nextFileId ->
                extractJobRepository.startFileProgress(nextFileId)
            }
        }
        notifyFileCountProgress()
    }

    private suspend fun clearProgress() {
        extractJobRepository.clearProgress(operationId)
        byteProgressFileId = null
        byteTotalBytes = 0
        latestTransferredBytes = 0
        lastPersistedBytes = -1
        lastPersistedAtMs = 0
        fileProgressIds = emptyList()
        nextFileIndex = 0
    }

    private fun notifyByteProgress(transferredBytes: Long) {
        if (byteTotalBytes <= 0) {
            onProgressChanged(null, null)
            return
        }
        val ratio = (transferredBytes.toFloat() / byteTotalBytes.toFloat()).coerceIn(0f, 1f)
        onProgressChanged(
            "${ExtractProgressText.formatFileSize(transferredBytes)}/${ExtractProgressText.formatFileSize(byteTotalBytes)}",
            ratio,
        )
    }

    private fun notifyFileCountProgress() {
        val totalFiles = fileProgressIds.size
        if (totalFiles <= 0) {
            onProgressChanged(null, null)
            return
        }
        val completedFiles = nextFileIndex.coerceAtMost(totalFiles)
        val ratio = (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        onProgressChanged(
            "$completedFiles/$totalFiles ファイル",
            ratio,
        )
    }

    private companion object {
        private const val MIN_BYTE_UPDATE_INTERVAL = 512L * 1024
        private const val MIN_TIME_UPDATE_INTERVAL_MS = 200L
    }
}
