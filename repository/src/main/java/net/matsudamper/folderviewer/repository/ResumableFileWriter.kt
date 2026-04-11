package net.matsudamper.folderviewer.repository

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.delay

internal class ResumableFileWriter(
    private val destinationFile: File,
    private val partialFile: File,
    private val sourceSize: Long,
    private val overwrite: Boolean,
    private val openInputStream: suspend (offset: Long) -> InputStream,
    private val onProgress: suspend (currentBytes: Long) -> Unit,
    private val shouldStop: () -> Boolean,
    private val retryDelaysMillis: LongArray = RETRY_DELAYS_MILLIS,
) {
    suspend fun copy(): Boolean {
        require(sourceSize >= 0L) { "sourceSize must be greater than or equal to 0: $sourceSize" }
        if (!overwrite && destinationFile.exists()) {
            throw FileAlreadyExistsException(destinationFile)
        }

        if (partialFile.exists() && partialFile.length() > sourceSize) {
            partialFile.delete()
        }
        if (!partialFile.exists()) {
            require(partialFile.createNewFile()) { "Failed to create partial file: ${partialFile.absolutePath}" }
        }

        var currentBytes = partialFile.length()
        onProgress(currentBytes)

        if (currentBytes == sourceSize) {
            finish()
            onProgress(sourceSize)
            return true
        }

        var failedAttempts = 0
        while (currentBytes < sourceSize) {
            if (shouldStop()) return false

            val result = copyFromOffset(currentBytes)
            currentBytes = result.currentBytes

            when (result) {
                is CopyResult.Completed -> {
                    failedAttempts = 0
                }

                is CopyResult.Stopped -> {
                    return false
                }

                is CopyResult.SourceFailed -> {
                    failedAttempts++
                    if (failedAttempts > MAX_RETRIES) {
                        return false
                    }
                    delay(retryDelaysMillis[(failedAttempts - 1).coerceAtMost(retryDelaysMillis.lastIndex)])
                }
            }
        }

        finish()
        onProgress(sourceSize)
        return true
    }

    private suspend fun copyFromOffset(startOffset: Long): CopyResult {
        val inputStream = try {
            openInputStream(startOffset)
        } catch (_: IOException) {
            return CopyResult.SourceFailed(startOffset)
        }

        var currentBytes = startOffset
        inputStream.use { input ->
            RandomAccessFile(partialFile, "rw").use { output ->
                output.seek(startOffset)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (currentBytes < sourceSize) {
                    if (shouldStop()) return CopyResult.Stopped(currentBytes)

                    val readLength = minOf(buffer.size, (sourceSize - currentBytes).toInt())
                    val read = try {
                        input.read(buffer, 0, readLength)
                    } catch (_: IOException) {
                        return CopyResult.SourceFailed(currentBytes)
                    }
                    if (read == -1) {
                        return CopyResult.SourceFailed(currentBytes)
                    }

                    output.write(buffer, 0, read)
                    currentBytes += read
                    onProgress(currentBytes.coerceAtMost(sourceSize))
                }
            }
        }
        return CopyResult.Completed(currentBytes)
    }

    private fun finish() {
        if (!overwrite && destinationFile.exists()) {
            throw FileAlreadyExistsException(destinationFile)
        }

        val source = partialFile.toPath()
        val destination = destinationFile.toPath()
        if (overwrite) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.move(source, destination)
        }
    }

    private sealed interface CopyResult {
        val currentBytes: Long

        data class Completed(override val currentBytes: Long) : CopyResult
        data class Stopped(override val currentBytes: Long) : CopyResult
        data class SourceFailed(override val currentBytes: Long) : CopyResult
    }

    private companion object {
        private const val MAX_RETRIES = 5
        private val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)
    }
}
