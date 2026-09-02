package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

internal object ExtractMediaScanner {
    fun scanExtractedMediaFiles(context: Context, files: List<File>) {
        val mediaPaths = files
            .asSequence()
            .filter { !it.isDirectory }
            .filter { FileUtil.isImage(it.name) || FileUtil.isVideo(it.name) }
            .map { it.absolutePath }
            .toList()
        if (mediaPaths.isEmpty()) {
            return
        }
        MediaScannerConnection.scanFile(context, mediaPaths.toTypedArray(), null, null)
    }
}
