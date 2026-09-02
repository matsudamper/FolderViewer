package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TarGzFileUtilTest {
    @Test
    fun extractTarGz_extractsValidArchive() {
        val tempDir = Files.createTempDirectory("tar-gz-file-util").toFile()
        try {
            val tarGzFile = File(tempDir, "archive.tar.gz")
            createTarGz(
                tarGzFile,
                listOf(
                    TarEntry("hello.txt", "hello".toByteArray()),
                ),
            )
            val destDir = File(tempDir, "output")
            val extractedFiles = TarGzFileUtil.extractTarGz(tarGzFile, destDir)
            assertTrue(destDir.isDirectory)
            assertEquals(listOf(File(destDir, "hello.txt")), extractedFiles)
            assertEquals("hello", File(destDir, "hello.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractTarGz_extractsTgzExtension() {
        val tempDir = Files.createTempDirectory("tar-gz-file-util").toFile()
        try {
            val tarGzFile = File(tempDir, "archive.tgz")
            createTarGz(
                tarGzFile,
                listOf(
                    TarEntry("hello.txt", "hello".toByteArray()),
                ),
            )
            val destDir = File(tempDir, "output")
            val extractedFiles = TarGzFileUtil.extractTarGz(tarGzFile, destDir)
            assertEquals(listOf(File(destDir, "hello.txt")), extractedFiles)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun tarGzDefaultFolderName_removesExtension() {
        assertEquals("archive", TarGzFileUtil.tarGzDefaultFolderName("archive.tar.gz"))
        assertEquals("archive", TarGzFileUtil.tarGzDefaultFolderName("archive.tgz"))
    }

    @Test
    fun extractTarGz_rejectsInvalidArchive() {
        val tempDir = Files.createTempDirectory("tar-gz-file-util").toFile()
        try {
            val invalidFile = File(tempDir, "invalid.tar.gz")
            invalidFile.writeText("not a tar.gz")
            val destDir = File(tempDir, "output")
            val result = runCatching { TarGzFileUtil.extractTarGz(invalidFile, destDir) }
            assertTrue(result.exceptionOrNull() is TarGzFileUtil.ExtractException.InvalidArchive)
            assertTrue(!destDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractTarGz_rejectsExistingOutputDirectory() {
        val tempDir = Files.createTempDirectory("tar-gz-file-util").toFile()
        try {
            val tarGzFile = File(tempDir, "archive.tar.gz")
            createTarGz(
                tarGzFile,
                listOf(
                    TarEntry("hello.txt", "hello".toByteArray()),
                ),
            )
            val destDir = File(tempDir, "output")
            destDir.mkdir()
            val result = runCatching { TarGzFileUtil.extractTarGz(tarGzFile, destDir) }
            assertTrue(result.exceptionOrNull() is TarGzFileUtil.ExtractException.OutputAlreadyExists)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private data class TarEntry(
        val name: String,
        val content: ByteArray,
    )

    private fun createTarGz(outputFile: File, entries: List<TarEntry>) {
        GZIPOutputStream(BufferedOutputStream(outputFile.outputStream())).use { gzipOut ->
            TarArchiveOutputStream(gzipOut).use { tarOut ->
                entries.forEach { entry ->
                    val tarEntry = TarArchiveEntry(entry.name)
                    tarEntry.size = entry.content.size.toLong()
                    tarOut.putArchiveEntry(tarEntry)
                    tarOut.write(entry.content)
                    tarOut.closeArchiveEntry()
                }
            }
        }
    }
}
