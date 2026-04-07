package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface UploadOperationDao {
    @Insert
    suspend fun insert(op: UploadOperationEntity)

    @Query("SELECT * FROM upload_operations WHERE operationId = :operationId")
    suspend fun getByOperationId(operationId: Long): UploadOperationEntity?
}
