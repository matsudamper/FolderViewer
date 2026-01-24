package net.matsudamper.folderviewer.viewmodel.util

import android.webkit.MimeTypeMap

object FileUtil {
    fun isImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return false

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        return mimeType?.startsWith("image/") == true
    }

    fun isVideo(name: String): Boolean {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return false

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        return mimeType?.startsWith("video/") == true
    }

    fun getMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
}