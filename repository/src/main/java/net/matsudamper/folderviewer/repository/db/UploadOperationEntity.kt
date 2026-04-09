package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_operations",
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
internal data class UploadOperationEntity(
    @PrimaryKey
    val operationId: Long,
    val isFolder: Boolean,
    val storageId: String,
    val fileObjectId: String,
    val displayPath: String,
)
