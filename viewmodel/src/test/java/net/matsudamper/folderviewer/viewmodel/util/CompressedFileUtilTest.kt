package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
