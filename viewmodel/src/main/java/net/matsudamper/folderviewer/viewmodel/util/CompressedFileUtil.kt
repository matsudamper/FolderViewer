package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream

internal object CompressedFileUtil {
    private const val XZ_MEMORY_LIMIT_KIB = 64 * 1024

    enum class Format(
        val extension: String,
    ) {
        Zst(".zst"),
        Xz(".xz"),
    }

    sealed class DecompressException(message: String) : Exception(message) {
        class LimitExceeded(message: String) : DecompressException(message)
    }

    fun detectFormat(fileName: String): Format? {
        return Format.entries.firstOrNull { format ->
            fileName.endsWith(format.extension, ignoreCase = true)
        }
    }

    fun defaultOutputName(fileName: String, format: Format): String {
        return fileName.dropLast(format.extension.length)
    }

    fun decompress(
        sourceFile: File,
        outputFile: File,
        format: Format,
        progressListener: ExtractProgressListener? = null,
    ) {
        when (format) {
            Format.Zst -> decompressZst(sourceFile, outputFile, progressListener)
            Format.Xz -> decompressXz(sourceFile, outputFile, progressListener)
        }
    }

    fun decompressGzip(
        sourceFile: File,
        outputFile: File,
        progressListener: ExtractProgressListener? = null,
    ) {
        val outputDirectory = outputFile.parentFile ?: outputFile
        CountingInputStream(BufferedInputStream(FileInputStream(sourceFile))).use { countingInput ->
            GzipCompressorInputStream.builder()
                .setInputStream(countingInput)
                .setDecompressConcatenated(true)
                .get()
                .use { gzipIn ->
                    BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                        copyWithLimit(
                            input = gzipIn,
                            output = output,
                            outputDirectory = outputDirectory,
                            cancellationCheck = { progressListener?.checkCancellation() },
                            onProgress = { progressListener?.onBytesTransferred(countingInput.bytesRead) },
                        )
                    }
                }
        }
    }

    private fun decompressZst(
        sourceFile: File,
        outputFile: File,
        progressListener: ExtractProgressListener?,
    ) {
        val outputDirectory = outputFile.parentFile ?: outputFile
        CountingInputStream(BufferedInputStream(FileInputStream(sourceFile))).use { countingInput ->
            ZstdInputStream(countingInput).use { zstInput ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    copyWithLimit(
                        input = zstInput,
                        output = output,
                        outputDirectory = outputDirectory,
                        cancellationCheck = { progressListener?.checkCancellation() },
                        onProgress = { progressListener?.onBytesTransferred(countingInput.bytesRead) },
                    )
                }
            }
        }
    }

    private fun decompressXz(
        sourceFile: File,
        outputFile: File,
        progressListener: ExtractProgressListener?,
    ) {
        val outputDirectory = outputFile.parentFile ?: outputFile
        CountingInputStream(BufferedInputStream(FileInputStream(sourceFile))).use { countingInput ->
            XZInputStream(countingInput, XZ_MEMORY_LIMIT_KIB).use { xzInput ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    copyWithLimit(
                        input = xzInput,
                        output = output,
                        outputDirectory = outputDirectory,
                        cancellationCheck = { progressListener?.checkCancellation() },
                        onProgress = { progressListener?.onBytesTransferred(countingInput.bytesRead) },
                    )
                }
            }
        }
    }

    private fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        outputDirectory: File,
        cancellationCheck: (() -> Unit)? = null,
        onProgress: (() -> Unit)? = null,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            cancellationCheck?.invoke()
            val read = input.read(buffer)
            if (read == -1) {
                break
            }
            ensureOutputSpaceAvailable(outputDirectory, read.toLong())
            output.write(buffer, 0, read)
            onProgress?.invoke()
        }
    }

    private class CountingInputStream(
        private val delegate: InputStream,
    ) : InputStream() {
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value != -1) {
                bytesRead++
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) {
                bytesRead += read
            }
            return read
        }

        override fun close() {
            delegate.close()
        }
    }

    private fun ensureOutputSpaceAvailable(outputDirectory: File, bytesToWrite: Long) {
        if (ExtractStorageLimit.canWrite(outputDirectory, bytesToWrite)) {
            return
        }
        throw DecompressException.LimitExceeded("展開サイズが上限を超えています")
    }
}
