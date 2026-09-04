package net.matsudamper.folderviewer.viewmodel.util

import java.util.Locale
import net.matsudamper.folderviewer.repository.OperationRepository

internal object ExtractProgressText {
    fun fromOperationProgress(progress: OperationRepository.OperationProgress): String? {
        return when {
            progress.totalBytes > 0 -> {
                "${formatFileSize(progress.completedBytes)}/${formatFileSize(progress.totalBytes)}"
            }

            progress.totalFiles > 0 -> {
                "${progress.completedFiles}/${progress.totalFiles} ファイル"
            }

            else -> null
        }
    }

    fun progressRatio(progress: OperationRepository.OperationProgress): Float? {
        return when {
            progress.totalBytes > 0 -> {
                (progress.completedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
            }

            progress.totalFiles > 0 -> {
                (progress.completedFiles.toFloat() / progress.totalFiles.toFloat()).coerceIn(0f, 1f)
            }

            else -> null
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) {
            return "$bytes B"
        }
        val kb = bytes / 1024.0
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb)
        }
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.1f GB", gb)
    }
}
