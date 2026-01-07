package net.matsudamper.folderviewer.viewmodel

import android.webkit.MimeTypeMap

object FileUtil {
    fun isImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return false

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        return mimeType?.startsWith("image/") == true
    }
}
