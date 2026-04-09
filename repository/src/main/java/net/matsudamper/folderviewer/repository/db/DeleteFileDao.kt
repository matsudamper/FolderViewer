package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DeleteFileDao {
    @Insert
    suspend fun insertAll(files: List<DeleteFileEntity>)

    @Query(
        "SELECT * FROM delete_files " +
            "WHERE operationId = :operationId AND completed = 0 AND errorMessage IS NULL " +
            "ORDER BY id ASC",
    )
    suspend fun getPendingByOperationId(operationId: Long): List<DeleteFileEntity>

    @Query("SELECT * FROM delete_files WHERE operationId = :operationId ORDER BY id ASC")
    fun observeByOperationId(operationId: Long): Flow<List<DeleteFileEntity>>

    @Query("UPDATE delete_files SET completed = 1 WHERE id = :fileId")
    suspend fun markCompleted(fileId: Long)

    @Query("UPDATE delete_files SET errorMessage = :errorMessage WHERE id = :fileId")
    suspend fun markFailed(fileId: Long, errorMessage: String?)
}
