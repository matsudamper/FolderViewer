package net.matsudamper.folderviewer.repository.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operations")
internal data class OperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val workerId: String?,
    val name: String,
    val description: String,
    val status: String,
    val pauseRequested: Boolean = false,
    val createdAt: Long,
    val errorMessage: String? = null,
    val errorCause: String? = null,
)
