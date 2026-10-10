package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class FileUtilTextViewerMimeTypeTest {
    @Test
    fun resolve_keepsTextMime() {
        assertEquals(
            "text/plain",
            FileUtil.resolveTextViewerMimeType("notes.txt", "text/plain"),
        )
    }

    @Test
    fun resolve_keepsJsonMime() {
        assertEquals(
            "application/json",
            FileUtil.resolveTextViewerMimeType("data.json", "application/json"),
        )
    }

    @Test
    fun resolve_usesPlainTextForSourceExtensionWithoutMime() {
        assertEquals(
            "text/plain",
            FileUtil.resolveTextViewerMimeType("Main.kt", null),
        )
    }

    @Test
    fun resolve_usesPlainTextForDotFile() {
        assertEquals(
            "text/plain",
            FileUtil.resolveTextViewerMimeType(".gitignore", null),
        )
    }

    @Test
    fun resolve_ignoresExtensionCase() {
        assertEquals(
            "text/plain",
            FileUtil.resolveTextViewerMimeType("Script.KT", null),
        )
    }

    @Test
    fun resolve_rejectsVideo() {
        assertNull(FileUtil.resolveTextViewerMimeType("movie.mp4", "video/mp4"))
    }

    @Test
    fun resolve_rejectsArchive() {
        assertNull(FileUtil.resolveTextViewerMimeType("archive.zip", "application/zip"))
    }

    @Test
    fun resolve_rejectsNameWithoutExtension() {
        assertNull(FileUtil.resolveTextViewerMimeType("README", null))
    }
}
