package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import com.github.luben.zstd.ZstdInputStream
import org.tukaani.xz.XZInputStream

internal object CompressedFileUtil {
    private const val MAX_OUTPUT_SIZE_BYTES = 2L * 1024 * 1024 * 1024
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

    fun decompress(sourceFile: File, outputFile: File, format: Format) {
        when (format) {
            Format.Zst -> decompressZst(sourceFile, outputFile)
            Format.Xz -> decompressXz(sourceFile, outputFile)
        }
    }

    private fun decompressZst(sourceFile: File, outputFile: File) {
        BufferedInputStream(FileInputStream(sourceFile)).use { input ->
            ZstdInputStream(input).use { zstInput ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    copyWithLimit(zstInput, output, MAX_OUTPUT_SIZE_BYTES)
                }
            }
        }
    }

    private fun decompressXz(sourceFile: File, outputFile: File) {
        BufferedInputStream(FileInputStream(sourceFile)).use { input ->
            XZInputStream(input, XZ_MEMORY_LIMIT_KIB).use { xzInput ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    copyWithLimit(xzInput, output, MAX_OUTPUT_SIZE_BYTES)
                }
            }
        }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) {
                break
            }
            total += read
            ensureOutputSizeWithinLimit(total, maxBytes)
            output.write(buffer, 0, read)
        }
    }

    private fun ensureOutputSizeWithinLimit(total: Long, maxBytes: Long) {
        if (total <= maxBytes) {
            return
        }
        throw DecompressException.LimitExceeded("展開サイズが上限を超えています")
    }
}
