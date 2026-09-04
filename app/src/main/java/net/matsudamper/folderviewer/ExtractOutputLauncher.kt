package net.matsudamper.folderviewer

import android.content.Context
import android.content.Intent
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
    ) {
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
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                )
            }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(intent)
        }
    }

    fun openOutputFolder(
        context: Context,
        absolutePath: String,
    ) {
        val relativePath = File(absolutePath)
            .relativeToOrNull(android.os.Environment.getExternalStorageDirectory())
            ?.path
            .orEmpty()
        val uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$relativePath",
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        }
        runCatching {
            context.startActivity(intent)
        }
    }
}
