package net.matsudamper.folderviewer.repository

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.delay

internal class ResumableFileWriter(
    private val request: Request,
    private val retryDelaysMillis: LongArray = RETRY_DELAYS_MILLIS,
) {
    private val destinationFile = request.destinationFile
    private val partialFile = request.partialFile
    private val sourceSize = request.sourceSize
    private val overwrite = request.overwrite
    private val openInputStream = request.openInputStream
    private val onProgress = request.onProgress
    private val shouldStop = request.shouldStop

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
        val inputStream = openInputStreamOrNull(startOffset) ?: return CopyResult.SourceFailed(startOffset)

        return inputStream.use { input ->
            RandomAccessFile(partialFile, "rw").use { output ->
                output.seek(startOffset)
                copyFromInput(input, output, startOffset)
            }
        }
    }

    private suspend fun copyFromInput(
        input: InputStream,
        output: RandomAccessFile,
        startOffset: Long,
    ): CopyResult {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var currentBytes = startOffset
        while (currentBytes < sourceSize) {
            if (shouldStop()) return CopyResult.Stopped(currentBytes)

            val readLength = minOf(buffer.size, (sourceSize - currentBytes).toInt())
            val read = input.readOrNull(buffer, readLength)
            if (read == null || read == -1) {
                return CopyResult.SourceFailed(currentBytes)
            }

            output.write(buffer, 0, read)
            currentBytes += read
            onProgress(currentBytes.coerceAtMost(sourceSize))
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

    private suspend fun openInputStreamOrNull(offset: Long): InputStream? {
        return try {
            openInputStream(offset)
        } catch (_: IOException) {
            null
        }
    }

    private fun InputStream.readOrNull(buffer: ByteArray, readLength: Int): Int? {
        return try {
            read(buffer, 0, readLength)
        } catch (_: IOException) {
            null
        }
    }

    internal data class Request(
        val destinationFile: File,
        val partialFile: File,
        val sourceSize: Long,
        val overwrite: Boolean,
        val openInputStream: suspend (offset: Long) -> InputStream,
        val onProgress: suspend (currentBytes: Long) -> Unit,
        val shouldStop: () -> Boolean,
    )

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
