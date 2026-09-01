package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface ExtractOperationDao {
    @Insert
    suspend fun insert(entity: ExtractOperationEntity)

    @Query("SELECT * FROM extract_operations WHERE operationId = :operationId")
    suspend fun getByOperationId(operationId: Long): ExtractOperationEntity?

    @Query("UPDATE extract_operations SET outputAbsolutePath = :outputAbsolutePath WHERE operationId = :operationId")
    suspend fun updateOutputAbsolutePath(operationId: Long, outputAbsolutePath: String)
}
