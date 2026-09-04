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
    private var fileProgressIds: List<Long> = emptyList()
    private var nextFileIndex = 0

    suspend fun startByteProgress(totalBytes: Long, label: String) {
        clearProgress()
        byteTotalBytes = totalBytes
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
        val fileId = byteProgressFileId ?: return
        runBlocking {
            extractJobRepository.updateByteProgress(fileId, transferredBytes)
        }
        notifyByteProgress(transferredBytes)
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
}
