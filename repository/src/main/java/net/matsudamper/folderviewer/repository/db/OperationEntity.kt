package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val workerId: String?,
    val name: String,
    val status: String,
    val createdAt: Long,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val failedFiles: Int = 0,
    val totalBytes: Long = 0,
    val completedBytes: Long = 0,
    val currentFileName: String? = null,
    val currentFileBytes: Long = 0,
    val currentFileTotalBytes: Long = 0,
    val errorMessage: String? = null,
    val errorCause: String? = null,
    val duplicateFiles: Int = 0,
    val resolvedFiles: Int = 0,
)
