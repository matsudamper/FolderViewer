package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TarArchiveUtilTest {
    @Test
    fun extractSingleFileEntry_extractsApkToSameDirectory() {
        val tempDir = Files.createTempDirectory("tar-archive-util").toFile()
        try {
            val tarFile = File(tempDir, "archive.tar")
            createTar(
                tarFile,
                listOf(
                    TarTestEntry(
                        name = "test.apk",
                        content = zipMagic(),
                    ),
                ),
            )
            val outputFile = File(tempDir, "test.apk")
            TarArchiveUtil.extractSingleFileEntry(
                tarFile = tarFile,
                entry = TarArchiveUtil.EntryInfo(name = "test.apk", size = 4, isDirectory = false),
                outputFile = outputFile,
            )
            assertTrue(outputFile.isFile)
            assertEquals(4, outputFile.length())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test(expected = SecurityException::class)
    fun extract_rejectsPathTraversal() {
        val tempDir = Files.createTempDirectory("tar-archive-util").toFile()
        try {
            val tarFile = File(tempDir, "archive.tar")
            createTar(
                tarFile,
                listOf(
                    TarTestEntry(
                        name = "../escape.txt",
                        content = "bad".toByteArray(),
                    ),
                ),
            )
            val destDir = File(tempDir, "output")
            TarArchiveUtil.extract(tarFile, destDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private data class TarTestEntry(
        val name: String,
        val content: ByteArray,
    )

    private fun createTar(tarFile: File, entries: List<TarTestEntry>) {
        FileOutputStream(tarFile).use { output ->
            entries.forEach { entry ->
                writeTarEntry(output, entry.name, entry.content)
            }
            output.write(ByteArray(1024))
        }
    }

    private fun writeTarEntry(output: FileOutputStream, name: String, content: ByteArray) {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        nameBytes.copyInto(header, destinationOffset = 0, endIndex = minOf(nameBytes.size, 100))
        writeOctal(header, 124, 12, content.size.toLong())
        writeOctal(header, 136, 12, 0L)
        writeOctal(header, 148, 12, 0L)
        header[156] = '0'.code.toByte()
        output.write(header)
        output.write(content)
        val padding = (512 - (content.size % 512)) % 512
        if (padding > 0) {
            output.write(ByteArray(padding))
        }
    }

    private fun writeOctal(header: ByteArray, offset: Int, length: Int, value: Long) {
        val text = String.format(Locale.US, "%0${length - 1}o", value)
        text.toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = offset)
        header[offset + length - 1] = 0
    }

    private fun zipMagic(): ByteArray {
        return byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}
