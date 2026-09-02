package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal object ExternalDirectoryUriInspector {
    fun isDirectory(context: Context, uri: Uri, mimeType: String? = null): Boolean {
        if (isDirectoryMimeType(mimeType)) {
            return true
        }
        if (isDirectoryMimeType(context.contentResolver.getType(uri))) {
            return true
        }
        DocumentFile.fromTreeUri(context, uri)?.let { document ->
            if (document.isDirectory) {
                return true
            }
        }
        DocumentFile.fromSingleUri(context, uri)?.let { document ->
            if (document.isDirectory) {
                return true
            }
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            return File(path).isDirectory
        }
        return false
    }

    fun isDirectoryMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) {
            return false
        }
        return mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
            mimeType == "resource/folder" ||
            mimeType == "vnd.android.document/directory"
    }
}
