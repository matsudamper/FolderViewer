package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_jobs")
internal data class UploadJobEntity(
    @PrimaryKey
    val workerId: String,
    val name: String,
    val isFolder: Boolean,
    val storageId: String,
    val fileObjectId: String,
    val displayPath: String,
    val createdAt: Long,
)
