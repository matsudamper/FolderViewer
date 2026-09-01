package net.matsudamper.folderviewer.viewmodel.util

import com.github.luben.zstd.ZstdInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.tukaani.xz.XZInputStream

internal object CompressedFileUtil {
    enum class Format(
        val extension: String,
    ) {
        Zst(".zst"),
        Xz(".xz"),
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
                    zstInput.copyTo(output)
                }
            }
        }
    }

    private fun decompressXz(sourceFile: File, outputFile: File) {
        BufferedInputStream(FileInputStream(sourceFile)).use { input ->
            XZInputStream(input).use { xzInput ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    xzInput.copyTo(output)
                }
            }
        }
    }
}
