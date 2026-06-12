package net.matsudamper.folderviewer.repository.db

import androidx.room.Embedded

internal data class OperationProgressRow(
    @Embedded val operation: OperationEntity,
    val pasteMode: String?,
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val unresolvedDuplicateFiles: Int,
    val totalBytes: Long,
    val completedBytes: Long,
    val currentFileName: String?,
    val currentFileBytes: Long?,
    val currentFileTotalBytes: Long?,
)
