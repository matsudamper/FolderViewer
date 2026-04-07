package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface PasteOperationDao {
    @Insert
    suspend fun insert(op: PasteOperationEntity)

    @Query("SELECT * FROM paste_operations WHERE operationId = :operationId")
    suspend fun getByOperationId(operationId: Long): PasteOperationEntity?
}
