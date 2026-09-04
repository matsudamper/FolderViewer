package net.matsudamper.folderviewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File
import net.matsudamper.folderviewer.repository.ViewSourceUri

internal object ExtractOutputLauncher {
    fun openOutputFile(
        context: Context,
        viewSourceUri: ViewSourceUri,
        fileName: String,
        mimeType: String?,
    ): Boolean {
        val uri = when (viewSourceUri) {
            is ViewSourceUri.LocalFile -> {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(viewSourceUri.path),
                )
            }

            is ViewSourceUri.RemoteUrl -> {
                android.net.Uri.parse(viewSourceUri.url)
            }

            is ViewSourceUri.StreamProvider -> {
                StreamingContentProvider.buildUri(
                    fileId = viewSourceUri.fileId,
                    fileName = fileName,
                )
            }
        }
        val isApk = mimeType == "application/vnd.android.package-archive"
        if (isApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                )
                false
            }.getOrDefault(false)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startActivitySafely(context, intent)
    }

    fun openOutputFolder(
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
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
