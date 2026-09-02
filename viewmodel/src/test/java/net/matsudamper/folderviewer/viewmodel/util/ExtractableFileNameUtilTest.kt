package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ExtractableFileNameUtilTest {
    @Test
    fun detect_returnsZipForZipExtension() {
        assertEquals(ExtractableFileType.Zip, ExtractableFileNameUtil.detect("archive.zip"))
    }

    @Test
    fun detect_returnsTarZstForTarZstExtension() {
        assertEquals(
            ExtractableFileType.TarZst,
            ExtractableFileNameUtil.detect("archive.tar.zst"),
        )
    }

    @Test
    fun detect_returnsTarXzForTarXzExtension() {
        assertEquals(
            ExtractableFileType.TarXz,
            ExtractableFileNameUtil.detect("archive.tar.xz"),
        )
    }

    @Test
    fun detect_returnsXzForApkXzApkSuffix() {
        assertEquals(
            ExtractableFileType.Compressed(CompressedFileUtil.Format.Xz),
            ExtractableFileNameUtil.detect("foo.apk.xz.apk"),
        )
    }

    @Test
    fun detect_returnsNullForUnsupportedExtension() {
        assertNull(ExtractableFileNameUtil.detect("archive.txt"))
    }

    @Test
    fun defaultOutputName_removesZipExtension() {
        assertEquals(
            "archive",
            ExtractableFileNameUtil.defaultOutputName(
                fileName = "archive.zip",
                type = ExtractableFileType.Zip,
            ),
        )
    }

    @Test
    fun defaultOutputName_returnsApkForApkXz() {
        assertEquals(
            "foo.apk",
            ExtractableFileNameUtil.defaultOutputName(
                fileName = "foo.apk.xz",
                type = ExtractableFileType.Compressed(CompressedFileUtil.Format.Xz),
            ),
        )
    }

    @Test
    fun defaultOutputName_returnsApkForApkXzApk() {
        assertEquals(
            "foo.apk",
            ExtractableFileNameUtil.defaultOutputName(
                fileName = "foo.apk.xz.apk",
                type = ExtractableFileType.Compressed(CompressedFileUtil.Format.Xz),
            ),
        )
    }

    @Test
    fun defaultOutputName_removesTarXzExtension() {
        assertEquals(
            "archive",
            ExtractableFileNameUtil.defaultOutputName(
                fileName = "archive.tar.xz",
                type = ExtractableFileType.TarXz,
            ),
        )
    }
}
