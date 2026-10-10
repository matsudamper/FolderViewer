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
        if (extension.equals("apk", ignoreCase = true)) {
            return "application/vnd.android.package-archive"
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }

    fun textViewerMimeType(fileName: String): String? {
        return resolveTextViewerMimeType(fileName, getMimeType(fileName))
    }

    internal fun resolveTextViewerMimeType(fileName: String, detectedMimeType: String?): String? {
        if (detectedMimeType != null && isTextViewerMime(detectedMimeType)) {
            return detectedMimeType
        }
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension in textViewerExtensions) return "text/plain"
        return null
    }

    private fun isTextViewerMime(mimeType: String): Boolean {
        if (mimeType.startsWith("text/", ignoreCase = true)) return true
        return mimeType.lowercase() in textViewerApplicationMimeTypes
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

    private val textViewerApplicationMimeTypes = setOf(
        "application/json",
        "application/ld+json",
        "application/xml",
        "application/javascript",
        "application/x-javascript",
        "application/xhtml+xml",
        "application/x-sh",
        "application/yaml",
        "application/x-yaml",
        "application/x-httpd-php",
    )

    private val textViewerExtensions = setOf(
        "txt",
        "text",
        "log",
        "md",
        "markdown",
        "json",
        "xml",
        "html",
        "htm",
        "css",
        "js",
        "mjs",
        "cjs",
        "ts",
        "tsx",
        "jsx",
        "csv",
        "tsv",
        "yaml",
        "yml",
        "sh",
        "bash",
        "zsh",
        "py",
        "kt",
        "kts",
        "java",
        "gradle",
        "properties",
        "ini",
        "conf",
        "cfg",
        "toml",
        "env",
        "sql",
        "php",
        "rb",
        "go",
        "rs",
        "c",
        "h",
        "cpp",
        "hpp",
        "cs",
        "swift",
        "dart",
        "vue",
        "gitignore",
        "editorconfig",
    )
}
