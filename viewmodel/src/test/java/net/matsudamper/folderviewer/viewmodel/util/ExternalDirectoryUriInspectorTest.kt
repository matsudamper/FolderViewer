package net.matsudamper.folderviewer.viewmodel.util

import android.provider.DocumentsContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDirectoryUriInspectorTest {
    @Test
    fun `directory mime types are recognized`() {
        assertTrue(ExternalDirectoryUriInspector.isDirectoryMimeType(DocumentsContract.Document.MIME_TYPE_DIR))
        assertTrue(ExternalDirectoryUriInspector.isDirectoryMimeType("resource/folder"))
        assertTrue(ExternalDirectoryUriInspector.isDirectoryMimeType("vnd.android.document/directory"))
    }

    @Test
    fun `non directory mime types are not recognized`() {
        assertFalse(ExternalDirectoryUriInspector.isDirectoryMimeType(null))
        assertFalse(ExternalDirectoryUriInspector.isDirectoryMimeType(""))
        assertFalse(ExternalDirectoryUriInspector.isDirectoryMimeType("application/zip"))
        assertFalse(ExternalDirectoryUriInspector.isDirectoryMimeType("application/octet-stream"))
    }
}
