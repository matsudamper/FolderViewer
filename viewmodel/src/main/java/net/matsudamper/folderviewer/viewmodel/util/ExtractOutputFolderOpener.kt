package net.matsudamper.folderviewer.viewmodel.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

object ExtractOutputFolderOpener {
    fun open(
        context: Context,
        absolutePath: String,
    ): Boolean {
        val folder = File(absolutePath)
        if (!folder.isDirectory) {
            return false
        }
        val primaryRelativePath = folder.relativeToOrNull(Environment.getExternalStorageDirectory())?.path
        if (primaryRelativePath != null && !primaryRelativePath.startsWith("..")) {
            val uri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$primaryRelativePath",
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            }
            if (startActivitySafely(context, intent)) {
                return true
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            folder,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startActivitySafely(context, intent)
    }

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
