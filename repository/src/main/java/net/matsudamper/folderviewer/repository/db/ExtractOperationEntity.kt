package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extract_operations",
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
internal data class ExtractOperationEntity(
    @PrimaryKey
    val operationId: Long,
    val sourceFileObjectId: String,
    val sourceFileName: String,
    val outputName: String,
    val extractType: String,
    val parentFileObjectId: String,
    val parentDisplayPath: String,
    val localFolderPath: String,
    val openOnComplete: Boolean,
    val openOnCompleteHandled: Boolean = false,
    val outputAbsolutePath: String? = null,
    val sourceAbsolutePath: String? = null,
)
