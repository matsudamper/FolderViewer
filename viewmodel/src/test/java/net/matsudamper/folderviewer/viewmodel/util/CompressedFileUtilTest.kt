package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

internal class CompressedFileUtilTest {
    @Test
    fun detectFormat_returnsZstForZstExtension() {
        assertEquals(CompressedFileUtil.Format.Zst, CompressedFileUtil.detectFormat("archive.tar.zst"))
    }

    @Test
    fun detectFormat_returnsXzForXzExtension() {
        assertEquals(CompressedFileUtil.Format.Xz, CompressedFileUtil.detectFormat("archive.tar.xz"))
    }

    @Test
    fun detectFormat_returnsNullForUnsupportedExtension() {
        assertNull(CompressedFileUtil.detectFormat("archive.zip"))
    }

    @Test
    fun defaultOutputName_removesExtension() {
        assertEquals(
            "archive.tar",
            CompressedFileUtil.defaultOutputName("archive.tar.zst", CompressedFileUtil.Format.Zst),
        )
        assertEquals(
            "archive.tar",
            CompressedFileUtil.defaultOutputName("archive.tar.xz", CompressedFileUtil.Format.Xz),
        )
    }

    @Test
    fun decompress_decompressesConcatenatedGzip() {
        val tempDir = Files.createTempDirectory("compressed-file-util").toFile()
        try {
            val source = File(tempDir, "data.txt.gz")
            val gzipBytes = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write("part1".toByteArray()) }
                GZIPOutputStream(this).use { it.write("part2".toByteArray()) }
            }.toByteArray()
            source.writeBytes(gzipBytes)
            val output = File(tempDir, "data.txt")
            CompressedFileUtil.decompressGzip(source, output)
            assertEquals("part1part2", output.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun decompress_decompressesXz() {
        val tempDir = Files.createTempDirectory("compressed-file-util").toFile()
        try {
            val source = File(tempDir, "data.txt.xz")
            val original = "hello xz"
            XZOutputStream(FileOutputStream(source), LZMA2Options()).use { it.write(original.toByteArray()) }
            val output = File(tempDir, "data.txt")
            CompressedFileUtil.decompress(source, output, CompressedFileUtil.Format.Xz)
            assertEquals(original, output.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
