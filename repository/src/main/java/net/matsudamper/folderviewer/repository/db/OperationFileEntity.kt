package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operation_files",
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("operationId"),
        Index(value = ["operationId", "status"]),
        Index(value = ["operationId", "resolution"]),
    ],
)
internal data class OperationFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: Long,
    val fileName: String,
    val relativePath: String = "",
    val isDirectory: Boolean = false,
    val fileSize: Long? = null,
    val status: String = "PENDING",
    val transferredBytes: Long = 0,
    val errorMessage: String? = null,
    val sourceFileId: String? = null,
    val destinationFileId: String? = null,
    val destinationFileSize: Long? = null,
    val resolution: String? = null,
    val sourceDeleted: Boolean = false,
)
