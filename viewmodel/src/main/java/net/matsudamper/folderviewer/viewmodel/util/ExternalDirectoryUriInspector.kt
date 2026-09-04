package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

internal object ExternalDirectoryUriInspector {
    fun isDirectory(context: Context, uri: Uri): Boolean {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)?.let { document ->
                if (document.isDirectory) {
                    return true
                }
            }
        }
        DocumentFile.fromSingleUri(context, uri)?.let { document ->
            if (document.isDirectory) {
                return true
            }
        }
        return isDirectoryMimeType(context.contentResolver.getType(uri))
    }

    fun resolveDirectoryMimeType(
        context: Context,
        uri: Uri,
        intentMimeType: String?,
    ): String {
        if (isDirectoryMimeType(intentMimeType)) {
            return requireNotNull(intentMimeType)
        }
        val resolverMimeType = context.contentResolver.getType(uri)
        if (isDirectoryMimeType(resolverMimeType)) {
            return requireNotNull(resolverMimeType)
        }
        return DocumentsContract.Document.MIME_TYPE_DIR
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
