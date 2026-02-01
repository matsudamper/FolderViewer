package net.matsudamper.folderviewer.repository.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UploadJobDao {
    @Query("SELECT * FROM upload_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<UploadJobEntity>>

    @Query("SELECT * FROM upload_jobs WHERE workerId = :workerId")
    suspend fun getJob(workerId: String): UploadJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: UploadJobEntity)

    @Query("DELETE FROM upload_jobs WHERE workerId = :workerId")
    suspend fun delete(workerId: String)

    @Query("DELETE FROM upload_jobs")
    suspend fun deleteAll()
}
