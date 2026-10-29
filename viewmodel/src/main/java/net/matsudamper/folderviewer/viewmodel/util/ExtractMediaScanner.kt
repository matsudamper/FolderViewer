package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

internal object ExtractMediaScanner {
    fun scanExtractedFiles(context: Context, files: List<File>) {
        val filePaths = files
            .filter { !it.isDirectory }
            .map { it.absolutePath }
        if (filePaths.isEmpty()) {
            return
        }
        MediaScannerConnection.scanFile(context, filePaths.toTypedArray(), null, null)
    }
}
