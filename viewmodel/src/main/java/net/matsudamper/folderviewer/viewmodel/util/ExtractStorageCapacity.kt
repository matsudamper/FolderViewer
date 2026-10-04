package net.matsudamper.folderviewer.viewmodel.util

import java.io.File

internal class ExtractStorageCapacity(outputDir: File) {
    private val availableBytes = outputDir.usableSpace
    private var writtenBytes = 0L

    fun consume(bytes: Long, insufficientException: (String) -> Exception) {
        writtenBytes += bytes
        if (writtenBytes > availableBytes) {
            throw insufficientException("展開先のストレージ容量が不足しています")
        }
    }

    companion object {
        fun forOutputFile(outputFile: File): ExtractStorageCapacity {
            return ExtractStorageCapacity(requireNotNull(outputFile.absoluteFile.parentFile))
        }
    }
}
