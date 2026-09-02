package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

internal object TarGzFileUtil {
    private const val MAX_ENTRY_COUNT = 10_000
    private const val MAX_ENTRY_SIZE_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_SIZE_BYTES = 2L * 1024 * 1024 * 1024

    sealed class ExtractException(message: String) : Exception(message) {
        class InvalidArchive(message: String) : ExtractException(message)

        class OutputAlreadyExists(name: String) : ExtractException("同じ名前のフォルダが既に存在します: $name")

        class LimitExceeded(message: String) : ExtractException(message)
    }

    fun tarGzDefaultFolderName(fileName: String): String {
        return when {
            fileName.endsWith(".tar.gz", ignoreCase = true) -> fileName.dropLast(7)
            fileName.endsWith(".tgz", ignoreCase = true) -> fileName.dropLast(4)
            else -> fileName
        }
    }

    fun extractTarGz(tarGzFile: File, destDir: File): List<File> {
        if (!destDir.mkdir()) {
            throw if (destDir.exists()) {
                ExtractException.OutputAlreadyExists(destDir.name)
            } else {
                ExtractException.InvalidArchive("展開先フォルダを作成できません")
            }
        }
        return try {
            extractTarGzContents(tarGzFile, destDir)
        } catch (e: Exception) {
            destDir.deleteRecursively()
            throw when (e) {
                is ExtractException, is SecurityException -> e
                else -> toExtractException(e)
            }
        }
    }

    private fun extractTarGzContents(tarGzFile: File, destDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        GzipCompressorInputStream(BufferedInputStream(FileInputStream(tarGzFile))).use { gzipIn ->
            TarArchiveInputStream(gzipIn).use { tarIn ->
                extractTarEntries(tarIn, destDir, extractedFiles)
            }
        }
        return extractedFiles
    }

    private fun extractTarEntries(
        tarIn: TarArchiveInputStream,
        destDir: File,
        extractedFiles: MutableList<File>,
    ) {
        var entryCount = 0
        var sawEntry = false
        var totalBytes = 0L
        var entry: TarArchiveEntry? = tarIn.nextEntry
        while (entry != null) {
            sawEntry = true
            entryCount++
            if (entryCount > MAX_ENTRY_COUNT) {
                throw ExtractException.LimitExceeded("アーカイブエントリ数が上限を超えています")
            }
            val written = extractTarEntry(tarIn, entry, destDir)
            totalBytes += written
            ensureTotalSizeWithinLimit(totalBytes)
            if (!entry.isDirectory) {
                extractedFiles += File(destDir, entry.name)
            }
            entry = tarIn.nextEntry
        }
        if (!sawEntry) {
            throw ExtractException.InvalidArchive("アーカイブにエントリがありません")
        }
    }

    private fun ensureTotalSizeWithinLimit(totalBytes: Long) {
        if (totalBytes <= MAX_TOTAL_SIZE_BYTES) {
            return
        }
        throw ExtractException.LimitExceeded("展開サイズが上限を超えています")
    }

    private fun toExtractException(e: Exception): ExtractException {
        return when (e) {
            is IOException -> ExtractException.InvalidArchive("アーカイブの展開に失敗しました")
            else -> ExtractException.InvalidArchive("アーカイブの展開に失敗しました")
        }
    }

    private fun extractTarEntry(
        tarIn: TarArchiveInputStream,
        entry: TarArchiveEntry,
        destDir: File,
    ): Long {
        val entryFile = File(destDir, entry.name)
        validateEntryPath(destDir, entryFile)
        if (entry.isDirectory) {
            entryFile.mkdirs()
            return 0L
        }
        entryFile.parentFile?.mkdirs()
        return FileOutputStream(entryFile).use { output ->
            copyWithLimit(tarIn, output, MAX_ENTRY_SIZE_BYTES)
        }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) {
                break
            }
            total += read
            ensureEntrySizeWithinLimit(total, maxBytes)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun ensureEntrySizeWithinLimit(total: Long, maxBytes: Long) {
        if (total <= maxBytes) {
            return
        }
        throw ExtractException.LimitExceeded("エントリサイズが上限を超えています")
    }

    private fun validateEntryPath(destDir: File, entryFile: File) {
        val destPath = destDir.canonicalPath
        val entryPath = entryFile.canonicalPath
        if (!entryPath.startsWith(destPath + File.separator) && entryPath != destPath) {
            throw SecurityException("Invalid archive entry path")
        }
    }
}
