package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ZipFileUtilTest {
    @Test
    fun extractZip_extractsValidArchive() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val zipFile = File(tempDir, "archive.zip")
            ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                zipOut.putNextEntry(ZipEntry("hello.txt"))
                zipOut.write("hello".toByteArray())
                zipOut.closeEntry()
            }
            val destDir = File(tempDir, "output")
            val extractedFiles = ZipFileUtil.extractZip(zipFile, destDir)
            assertTrue(destDir.isDirectory)
            assertEquals(listOf(File(destDir, "hello.txt")), extractedFiles)
            assertEquals("hello", File(destDir, "hello.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractZip_rejectsInvalidArchive() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val invalidFile = File(tempDir, "invalid.zip")
            invalidFile.writeText("not a zip")
            val destDir = File(tempDir, "output")
            val result = runCatching { ZipFileUtil.extractZip(invalidFile, destDir) }
            assertTrue(result.exceptionOrNull() is ZipFileUtil.ExtractException.InvalidArchive)
            assertTrue(!destDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractZip_rejectsEmptyArchive() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val zipFile = File(tempDir, "empty.zip")
            ZipOutputStream(zipFile.outputStream()).use { }
            val destDir = File(tempDir, "output")
            val result = runCatching { ZipFileUtil.extractZip(zipFile, destDir) }
            assertTrue(result.exceptionOrNull() is ZipFileUtil.ExtractException.InvalidArchive)
            assertTrue(!destDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractZip_rejectsExistingOutputDirectory() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val zipFile = File(tempDir, "archive.zip")
            ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                zipOut.putNextEntry(ZipEntry("hello.txt"))
                zipOut.write("hello".toByteArray())
                zipOut.closeEntry()
            }
            val destDir = File(tempDir, "output")
            destDir.mkdir()
            val result = runCatching { ZipFileUtil.extractZip(zipFile, destDir) }
            assertTrue(result.exceptionOrNull() is ZipFileUtil.ExtractException.OutputAlreadyExists)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun extractZip_rejectsTooManyEntries() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val zipFile = File(tempDir, "many.zip")
            ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                repeat(10_001) { index ->
                    zipOut.putNextEntry(ZipEntry("file$index.txt"))
                    zipOut.write(byteArrayOf(0))
                    zipOut.closeEntry()
                }
            }
            val destDir = File(tempDir, "output")
            val result = runCatching { ZipFileUtil.extractZip(zipFile, destDir) }
            assertTrue(result.exceptionOrNull() is ZipFileUtil.ExtractException.LimitExceeded)
            assertTrue(!destDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun listFileEntries_rejectsTooManyEntries() {
        val tempDir = Files.createTempDirectory("zip-file-util").toFile()
        try {
            val zipFile = File(tempDir, "many.zip")
            ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                repeat(10_001) { index ->
                    zipOut.putNextEntry(ZipEntry("file$index.txt"))
                    zipOut.write(byteArrayOf(0))
                    zipOut.closeEntry()
                }
            }
            val result = runCatching { ZipFileUtil.listFileEntries(zipFile) }
            assertTrue(result.exceptionOrNull() is ZipFileUtil.ExtractException.LimitExceeded)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
