package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paste_files",
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("operationId")],
)
internal data class PasteFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: Long,
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
    val errorMessage: String? = null,
)
