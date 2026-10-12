package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal object ExtractStorageLimit {
    internal const val RECHECK_INTERVAL_BYTES = 64L * 1024 * 1024
    private const val RESERVED_FREE_SPACE_BYTES = 1024L * 1024 * 1024

    fun canWrite(outputDirectory: File, bytesToWrite: Long): Boolean {
        return outputDirectory.usableSpace - RESERVED_FREE_SPACE_BYTES >= bytesToWrite
    }
}

internal class OutputSpaceGuard(
    private val hasSpace: (bytesToWrite: Long) -> Boolean,
) {
    private var bytesUntilCheck = 0L

    constructor(outputDirectory: File) : this(
        hasSpace = { bytesToWrite ->
            ExtractStorageLimit.canWrite(outputDirectory, bytesToWrite)
        },
    )

    fun canWrite(bytesToWrite: Long): Boolean {
        if (bytesUntilCheck > 0L) {
            bytesUntilCheck -= bytesToWrite
            return true
        }
        bytesUntilCheck = ExtractStorageLimit.RECHECK_INTERVAL_BYTES - bytesToWrite
        return hasSpace(bytesToWrite)
    }
}
