package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paste_files",
    foreignKeys = [
        ForeignKey(
            entity = PasteJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
internal data class PasteFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val sourceFileId: String,
    val fileName: String,
    val fileSize: Long,
    val completed: Boolean = false,
    val deleted: Boolean = false,
    val destinationRelativePath: String = "",
    val isDirectory: Boolean = false,
    val isDuplicate: Boolean = false,
    val destinationFileId: String? = null,
    val destinationFileSize: Long = 0,
    val resolution: String? = null,
)
