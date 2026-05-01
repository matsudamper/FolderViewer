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

    fun matchesMimeFilter(fileName: String, acceptedMimeTypes: List<String>): Boolean {
        if (acceptedMimeTypes.isEmpty()) return true
        if (acceptedMimeTypes.any { it == "*/*" }) return true
        val fileMimeType = getMimeType(fileName) ?: return true
        return acceptedMimeTypes.any { pattern ->
            if (pattern.endsWith("/*")) {
                fileMimeType.startsWith(pattern.substringBefore("/*") + "/")
            } else {
                fileMimeType.equals(pattern, ignoreCase = true)
            }
        }
    }
}
