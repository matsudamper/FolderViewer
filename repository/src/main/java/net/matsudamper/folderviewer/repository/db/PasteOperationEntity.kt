package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paste_operations",
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
internal data class PasteOperationEntity(
    @PrimaryKey
    val operationId: Long,
    val mode: String,
    val destinationFileObjectId: String,
    val destinationDisplayPath: String,
)
