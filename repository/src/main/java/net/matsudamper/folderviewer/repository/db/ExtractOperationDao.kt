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

    @Query("UPDATE extract_operations SET openOnCompleteHandled = 1 WHERE operationId = :operationId")
    suspend fun markOpenOnCompleteHandled(operationId: Long)

    @Query("UPDATE extract_operations SET openOnComplete = 0 WHERE operationId = :operationId")
    suspend fun disableOpenOnComplete(operationId: Long)

    @Query(
        """
        SELECT eo.operationId FROM extract_operations AS eo
        INNER JOIN operations AS op ON op.id = eo.operationId
        WHERE op.type = 'EXTRACT'
        AND op.status IN ('ENQUEUED', 'RUNNING')
        """,
    )
    suspend fun getActiveOperationIds(): List<Long>

    @Query(
        """
        SELECT eo.operationId FROM extract_operations AS eo
        INNER JOIN operations AS op ON op.id = eo.operationId
        WHERE eo.openOnComplete = 1
        AND eo.openOnCompleteHandled = 0
        AND op.status = 'COMPLETED'
        """,
    )
    suspend fun getPendingOpenOnCompleteOperationIds(): List<Long>
}
