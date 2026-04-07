package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "delete_files",
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
internal data class DeleteFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: Long,
    val sourceFileId: String,
    val fileName: String,
    val fileSize: Long,
    val isDirectory: Boolean,
    val parentRelativePath: String = "",
    val completed: Boolean = false,
    val errorMessage: String? = null,
)
