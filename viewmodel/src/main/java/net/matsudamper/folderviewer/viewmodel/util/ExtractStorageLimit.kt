package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal object ExtractStorageLimit {
    private const val RESERVED_FREE_SPACE_BYTES = 1024L * 1024 * 1024

    fun canWrite(outputDirectory: File, bytesToWrite: Long): Boolean {
        return outputDirectory.usableSpace - RESERVED_FREE_SPACE_BYTES >= bytesToWrite
    }
}
