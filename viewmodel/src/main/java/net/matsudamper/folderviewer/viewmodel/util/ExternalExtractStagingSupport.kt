package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal object ExternalExtractStagingSupport {
    private const val STAGING_DIR_NAME = "external-extract-source"
    private const val STAGING_FILE_PREFIX = "source-"

    fun isStagedSource(path: String): Boolean {
        val file = File(path)
        return file.parentFile?.name == STAGING_DIR_NAME &&
            file.name.startsWith(STAGING_FILE_PREFIX)
    }

    fun deleteStagedSourceIfNeeded(path: String?) {
        if (path == null) {
            return
        }
        if (!isStagedSource(path)) {
            return
        }
        File(path).delete()
    }
}
