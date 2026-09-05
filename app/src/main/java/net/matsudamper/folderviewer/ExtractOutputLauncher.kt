package net.matsudamper.folderviewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputFolderOpener

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
        return ExtractOutputFolderOpener.open(context, absolutePath)
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
