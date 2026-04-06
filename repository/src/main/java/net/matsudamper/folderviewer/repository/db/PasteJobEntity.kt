package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paste_jobs")
internal data class PasteJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workerId: String?,
    val mode: String,
    val destinationFileObjectId: String,
    val destinationDisplayPath: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val status: String,
    val currentFileName: String? = null,
    val currentFileBytes: Long = 0,
    val currentFileTotalBytes: Long = 0,
    val completedFiles: Int = 0,
    val completedBytes: Long = 0,
    val createdAt: Long,
    val errorMessage: String? = null,
    val errorCause: String? = null,
)
