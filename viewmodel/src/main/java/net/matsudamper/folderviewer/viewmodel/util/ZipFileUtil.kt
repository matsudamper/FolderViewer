package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

internal object ZipFileUtil {
    private const val MAX_ENTRY_COUNT = 10_000
    private val ZIP_NAME_CHARSET: Charset = Charset.forName("Cp437")

    sealed class ExtractException(message: String) : Exception(message) {
        class InvalidArchive(message: String) : ExtractException(message)

        class OutputAlreadyExists(name: String) : ExtractException("同じ名前のフォルダが既に存在します: $name")

        class LimitExceeded(message: String) : ExtractException(message)
    }

    fun addZipEntry(zipOut: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            zipOut.putNextEntry(ZipEntry("$entryName/"))
            zipOut.closeEntry()
            file.listFiles()?.forEach { child ->
                addZipEntry(zipOut, child, "$entryName/${child.name}")
            }
        } else {
            zipOut.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { input -> input.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    fun compressFiles(sourceFiles: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            sourceFiles.forEach { file -> addZipEntry(zipOut, file, file.name) }
        }
    }

    fun extractZip(
        zipFile: File,
        destDir: File,
        progressListener: ExtractProgressListener? = null,
    ): List<File> {
        val maxTotalSizeBytes = ExtractStorageLimit.maxWritableBytes(destDir.parentFile ?: destDir)
        if (!destDir.mkdir()) {
            throw if (destDir.exists()) {
                ExtractException.OutputAlreadyExists(destDir.name)
            } else {
                ExtractException.InvalidArchive("展開先フォルダを作成できません")
            }
        }
        return try {
            extractZipContents(zipFile, destDir, progressListener, maxTotalSizeBytes)
        } catch (e: Exception) {
            destDir.deleteRecursively()
            throw when (e) {
                is CancellationException -> e
                is ExtractException, is SecurityException -> e
                else -> toExtractException(e)
            }
        }
    }

    fun zipFileDefaultFolderName(fileName: String): String {
        return if (fileName.endsWith(".zip", ignoreCase = true)) {
            fileName.dropLast(4)
        } else {
            fileName
        }
    }

    fun listFileEntries(zipFile: File): List<String> {
        val fileNames = mutableListOf<String>()
        ZipFile(zipFile, ZIP_NAME_CHARSET).use { zip ->
            var fileEntryCount = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                fileEntryCount = nextFileEntryCount(fileEntryCount)
                fileNames += entry.name
            }
        }
        return fileNames
    }

    private fun nextFileEntryCount(currentCount: Int): Int {
        val nextCount = currentCount + 1
        if (nextCount > MAX_ENTRY_COUNT) {
            throw ExtractException.LimitExceeded("ZIPエントリ数が上限を超えています")
        }
        return nextCount
    }

    private fun extractZipContents(
        zipFile: File,
        destDir: File,
        progressListener: ExtractProgressListener?,
        maxTotalSizeBytes: Long,
    ): List<File> {
        val extractedFiles = mutableListOf<File>()
        ZipFile(zipFile, ZIP_NAME_CHARSET).use { zip ->
            val entries = zip.entries()
            var entryCount = 0
            var sawEntry = false
            var totalBytes = 0L
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                sawEntry = true
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw ExtractException.LimitExceeded("ZIPエントリ数が上限を超えています")
                }
                val remainingBytes = (maxTotalSizeBytes - totalBytes).coerceAtLeast(0L)
                val written = extractZipEntry(
                    zip = zip,
                    entry = entry,
                    destDir = destDir,
                    maxBytes = remainingBytes,
                    progressListener = progressListener,
                )
                totalBytes += written
                ensureTotalSizeWithinLimit(totalBytes, maxTotalSizeBytes)
                if (!entry.isDirectory) {
                    extractedFiles += File(destDir, entry.name)
                    progressListener?.onFileCompleted()
                }
            }
            if (!sawEntry) {
                throw ExtractException.InvalidArchive("ZIPファイルにエントリがありません")
            }
        }
        return extractedFiles
    }

    private fun ensureTotalSizeWithinLimit(totalBytes: Long, maxBytes: Long) {
        if (totalBytes <= maxBytes) {
            return
        }
        throw ExtractException.LimitExceeded("展開サイズが上限を超えています")
    }

    private fun toExtractException(e: Exception): ExtractException {
        return when (e) {
            is ZipException -> ExtractException.InvalidArchive("ZIPファイル形式が不正です")
            is IOException -> ExtractException.InvalidArchive("ZIPファイルの展開に失敗しました")
            else -> ExtractException.InvalidArchive("ZIPファイルの展開に失敗しました")
        }
    }

    private fun extractZipEntry(
        zip: ZipFile,
        entry: ZipEntry,
        destDir: File,
        maxBytes: Long,
        progressListener: ExtractProgressListener?,
    ): Long {
        val entryFile = File(destDir, entry.name)
        validateZipEntryPath(destDir, entryFile)
        if (entry.isDirectory) {
            entryFile.mkdirs()
            return 0L
        }
        entryFile.parentFile?.mkdirs()
        return zip.getInputStream(entry).use { input ->
            entryFile.outputStream().use { output ->
                copyWithLimit(input, output, maxBytes, progressListener)
            }
        }
    }

    private fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        progressListener: ExtractProgressListener?,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            progressListener?.checkCancellation()
            val read = input.read(buffer)
            if (read == -1) {
                break
            }
            total += read
            ensureTotalSizeWithinLimit(total, maxBytes)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun validateZipEntryPath(destDir: File, entryFile: File) {
        val destPath = destDir.canonicalPath
        val entryPath = entryFile.canonicalPath
        if (!entryPath.startsWith(destPath + File.separator) && entryPath != destPath) {
            throw SecurityException("Invalid zip entry path")
        }
    }
}
