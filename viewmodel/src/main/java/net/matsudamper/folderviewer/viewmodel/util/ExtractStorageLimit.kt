package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal object ExtractStorageLimit {
    private const val RESERVED_FREE_SPACE_BYTES = 1024L * 1024 * 1024

    fun maxWritableBytes(outputDirectory: File): Long {
        return (outputDirectory.usableSpace - RESERVED_FREE_SPACE_BYTES).coerceAtLeast(0L)
    }
}
