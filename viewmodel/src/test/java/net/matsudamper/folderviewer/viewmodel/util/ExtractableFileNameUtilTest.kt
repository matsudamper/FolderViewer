package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ExtractableFileNameUtilTest {
    @Test
    fun detect_returnsZipForZipExtension() {
        assertEquals(ExtractableFileNameUtil.detect("archive.zip"), ExtractableFileType.Zip)
    }

    @Test
    fun detect_returnsZstForZstExtension() {
        assertEquals(
            ExtractableFileType.Compressed(CompressedFileUtil.Format.Zst),
            ExtractableFileNameUtil.detect("archive.tar.zst"),
        )
    }

    @Test
    fun detect_returnsNullForUnsupportedExtension() {
        assertNull(ExtractableFileNameUtil.detect("archive.txt"))
    }

    @Test
    fun defaultOutputName_removesExtension() {
        assertEquals(
            "archive",
            ExtractableFileNameUtil.defaultOutputName(
                fileName = "archive.zip",
                type = ExtractableFileType.Zip,
            ),
        )
    }
}
