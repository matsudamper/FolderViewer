package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasteJobDao {
    @Query("SELECT * FROM paste_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<PasteJobEntity>>

    @Query("SELECT * FROM paste_jobs WHERE workerId = :workerId")
    suspend fun getJobByWorkerId(workerId: String): PasteJobEntity?

    @Query("SELECT * FROM paste_jobs WHERE id = :id")
    suspend fun getJobById(id: Long): PasteJobEntity?

    @Insert
    suspend fun insert(job: PasteJobEntity): Long

    @Query("UPDATE paste_jobs SET status = :status, workerId = :workerId WHERE id = :id")
    suspend fun updateStatusAndWorkerId(id: Long, status: String, workerId: String?)

    @Query(
        "UPDATE paste_jobs SET completedFiles = :completedFiles, completedBytes = :completedBytes WHERE id = :id",
    )
    suspend fun updateCompletedProgress(id: Long, completedFiles: Int, completedBytes: Long)

    @Query(
        "UPDATE paste_jobs SET currentFileName = :currentFileName, currentFileBytes = :currentFileBytes, " +
            "currentFileTotalBytes = :currentFileTotalBytes WHERE id = :id",
    )
    suspend fun updateCurrentFile(id: Long, currentFileName: String?, currentFileBytes: Long, currentFileTotalBytes: Long)

    @Query("UPDATE paste_jobs SET status = :status, errorMessage = :errorMessage, errorCause = :errorCause WHERE id = :id")
    suspend fun updateError(id: Long, status: String, errorMessage: String?, errorCause: String?)

    @Query("DELETE FROM paste_jobs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
