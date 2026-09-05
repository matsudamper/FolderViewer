package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal object ExternalExtractStagingSupport {
    private const val STAGING_DIR_NAME = "external-extract-source"
    private const val STAGING_FILE_PREFIX = "source-"

    fun stagingDirectory(cacheDir: File): File {
        return File(cacheDir, STAGING_DIR_NAME)
    }

    fun isStagedSource(path: String, cacheDir: File): Boolean {
        val stagingDirectory = stagingDirectory(cacheDir).canonicalFile
        val file = File(path).canonicalFile
        if (!file.name.startsWith(STAGING_FILE_PREFIX)) {
            return false
        }
        val parent = file.parentFile ?: return false
        return parent == stagingDirectory
    }

    fun deleteStagedSourceIfNeeded(path: String?, cacheDir: File) {
        if (path == null) {
            return
        }
        if (!isStagedSource(path, cacheDir)) {
            return
        }
        File(path).delete()
    }
}
