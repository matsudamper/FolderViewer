package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

internal object TarArchiveUtil {
    private const val BLOCK_SIZE = 512
    private const val MAX_ENTRY_COUNT = 10_000
    private const val MAX_ENTRY_SIZE_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_SIZE_BYTES = 2L * 1024 * 1024 * 1024

    sealed class ExtractException(message: String) : Exception(message) {
        class InvalidArchive(message: String) : ExtractException(message)

        class OutputAlreadyExists(name: String) : ExtractException("同じ名前のファイルが既に存在します: $name")

        class LimitExceeded(message: String) : ExtractException(message)
    }

    data class EntryInfo(
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val isUnsupportedLink: Boolean = false,
    )

    fun listEntries(tarFile: File): List<EntryInfo> {
        FileInputStream(tarFile).use { input ->
            return readEntries(input)
        }
    }

    fun extract(tarFile: File, destDir: File): List<File> {
        if (!destDir.mkdir()) {
            throw if (destDir.exists()) {
                ExtractException.OutputAlreadyExists(destDir.name)
            } else {
                ExtractException.InvalidArchive("展開先フォルダを作成できません")
            }
        }
        return try {
            extractContents(tarFile, destDir)
        } catch (e: Exception) {
            destDir.deleteRecursively()
            throw when (e) {
                is ExtractException, is SecurityException -> e
                else -> ExtractException.InvalidArchive("tarアーカイブの展開に失敗しました")
            }
        }
    }

    fun extractSingleFileEntry(tarFile: File, entry: EntryInfo, outputFile: File): File {
        validateEntryName(entry.name)
        outputFile.parentFile?.mkdirs()
        return copyMatchingEntry(tarFile, entry, outputFile)
            ?: throw ExtractException.InvalidArchive("tarエントリが見つかりません")
    }

    private fun copyMatchingEntry(tarFile: File, entry: EntryInfo, outputFile: File): File? {
        FileInputStream(tarFile).use { input ->
            return findAndCopyEntry(input, entry, outputFile)
        }
    }

    private fun findAndCopyEntry(input: InputStream, entry: EntryInfo, outputFile: File): File? {
        var header = readHeader(input)
        while (header != null) {
            val current = parseEntry(header)
            val copied = tryCopyEntry(input, current, entry, outputFile)
            if (copied != null) {
                return copied
            }
            skipEntryData(input, current.size)
            header = readHeader(input)
        }
        return null
    }

    private fun tryCopyEntry(
        input: InputStream,
        current: EntryInfo,
        target: EntryInfo,
        outputFile: File,
    ): File? {
        if (current.isDirectory || current.isUnsupportedLink || current.name != target.name) {
            return null
        }
        outputFile.outputStream().use { output ->
            copyEntryData(input, output, current.size)
        }
        return outputFile
    }

    private fun extractContents(tarFile: File, destDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        FileInputStream(tarFile).use { input ->
            var entryCount = 0
            var totalBytes = 0L
            var header = readHeader(input)
            while (header != null) {
                val entry = parseEntry(header)
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw ExtractException.LimitExceeded("tarエントリ数が上限を超えています")
                }
                totalBytes = extractEntry(input, entry, destDir, extractedFiles, totalBytes)
                header = readHeader(input)
            }
        }
        if (extractedFiles.isEmpty()) {
            throw ExtractException.InvalidArchive("tarアーカイブにファイルがありません")
        }
        return extractedFiles
    }

    private fun extractEntry(
        input: InputStream,
        entry: EntryInfo,
        destDir: File,
        extractedFiles: MutableList<File>,
        totalBytes: Long,
    ): Long {
        validateEntryName(entry.name)
        if (entry.isUnsupportedLink) {
            skipEntryData(input, entry.size)
            throw ExtractException.InvalidArchive("シンボリックリンクまたはハードリンクはサポートされていません")
        }
        val entryFile = File(destDir, entry.name)
        validateEntryPath(destDir, entryFile)
        if (entry.isDirectory) {
            entryFile.mkdirs()
            skipEntryData(input, entry.size)
            return totalBytes
        }
        entryFile.parentFile?.mkdirs()
        val written = entryFile.outputStream().use { output ->
            copyEntryData(input, output, entry.size)
        }
        val updatedTotal = totalBytes + written
        ensureTotalSizeWithinLimit(updatedTotal)
        extractedFiles += entryFile
        return updatedTotal
    }

    private fun readEntries(input: InputStream): List<EntryInfo> {
        val entries = mutableListOf<EntryInfo>()
        var entryCount = 0
        var header = readHeader(input)
        while (header != null) {
            val entry = parseEntry(header)
            entryCount++
            if (entryCount > MAX_ENTRY_COUNT) {
                throw ExtractException.LimitExceeded("tarエントリ数が上限を超えています")
            }
            entries += entry
            skipEntryData(input, entry.size)
            header = readHeader(input)
        }
        return entries
    }

    private fun readHeader(input: InputStream): ByteArray? {
        val header = ByteArray(BLOCK_SIZE)
        var offset = 0
        while (offset < BLOCK_SIZE) {
            val read = input.read(header, offset, BLOCK_SIZE - offset)
            if (read == -1) {
                return if (offset == 0) null else throw ExtractException.InvalidArchive("tarヘッダーが不正です")
            }
            offset += read
        }
        if (header.all { it == 0.toByte() }) {
            return null
        }
        return header
    }

    private fun parseEntry(header: ByteArray): EntryInfo {
        val rawName = parseString(header, 0, 100)
        val prefix = if (isUstarHeader(header)) {
            parseString(header, 345, 155)
        } else {
            ""
        }
        val fullName = when {
            prefix.isEmpty() -> rawName
            rawName.isEmpty() -> prefix
            else -> "$prefix/$rawName"
        }
        val size = parseOctal(header, 124, 12)
        val typeFlag = header[156].toInt().toChar()
        val isDirectory = typeFlag == '5' || fullName.endsWith('/')
        val isUnsupportedLink = typeFlag == '1' || typeFlag == '2'
        val name = fullName.trimEnd('/')
        return EntryInfo(name = name, size = size, isDirectory = isDirectory, isUnsupportedLink = isUnsupportedLink)
    }

    private fun isUstarHeader(header: ByteArray): Boolean {
        if (header.size < 263) {
            return false
        }
        return header[257] == 'u'.code.toByte() &&
            header[258] == 's'.code.toByte() &&
            header[259] == 't'.code.toByte() &&
            header[260] == 'a'.code.toByte() &&
            header[261] == 'r'.code.toByte()
    }

    private fun parseString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: (offset + length)
        return String(header, offset, end - offset, Charsets.US_ASCII)
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        val value = parseString(header, offset, length).trim { it <= ' ' }
        if (value.isEmpty()) {
            return 0L
        }
        return value.toLong(8)
    }

    private fun skipEntryData(input: InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(BLOCK_SIZE)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) {
                throw ExtractException.InvalidArchive("tarエントリが途中で終端しています")
            }
            remaining -= read
        }
        val padding = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE
        if (padding > 0) {
            val skipped = input.skip(padding)
            if (skipped < padding) {
                throw ExtractException.InvalidArchive("tarエントリが途中で終端しています")
            }
        }
    }

    private fun copyEntryData(input: InputStream, output: OutputStream, size: Long): Long {
        val buffer = ByteArray(BLOCK_SIZE)
        var remaining = size
        var total = 0L
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) {
                throw ExtractException.InvalidArchive("tarエントリが途中で終端しています")
            }
            ensureEntrySizeWithinLimit(total + read)
            output.write(buffer, 0, read)
            total += read
            remaining -= read
        }
        val padding = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE
        if (padding > 0) {
            val skipped = input.skip(padding)
            if (skipped < padding) {
                throw ExtractException.InvalidArchive("tarエントリが途中で終端しています")
            }
        }
        return total
    }

    private fun ensureEntrySizeWithinLimit(total: Long) {
        if (total <= MAX_ENTRY_SIZE_BYTES) {
            return
        }
        throw ExtractException.LimitExceeded("エントリサイズが上限を超えています")
    }

    private fun ensureTotalSizeWithinLimit(totalBytes: Long) {
        if (totalBytes <= MAX_TOTAL_SIZE_BYTES) {
            return
        }
        throw ExtractException.LimitExceeded("展開サイズが上限を超えています")
    }

    private fun validateEntryName(name: String) {
        val isInvalid = name.isEmpty() ||
            name == "." ||
            name == ".." ||
            name.startsWith("/") ||
            name.startsWith("\\") ||
            name.split('/', '\\').any { it == ".." }
        if (isInvalid) {
            throw SecurityException("Invalid tar entry path")
        }
    }

    private fun validateEntryPath(destDir: File, entryFile: File) {
        val destPath = destDir.canonicalPath
        val entryPath = entryFile.canonicalPath
        if (!entryPath.startsWith(destPath + File.separator) && entryPath != destPath) {
            throw SecurityException("Invalid tar entry path")
        }
    }
}
