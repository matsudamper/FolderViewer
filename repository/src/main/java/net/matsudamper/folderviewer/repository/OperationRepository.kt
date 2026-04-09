package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.matsudamper.folderviewer.repository.db.OperationDao
import net.matsudamper.folderviewer.repository.db.OperationEntity

@Singleton
class OperationRepository @Inject internal constructor(
    private val operationDao: OperationDao,
) {
    fun observeAll(limit: Int = 500): Flow<List<OperationEntity>> {
        return operationDao.observeAll(limit)
    }

    suspend fun deleteNonActiveHistory() {
        operationDao.deleteNonActive()
    }

    suspend fun deleteById(id: Long) {
        operationDao.deleteById(id)
    }

    suspend fun updateStatusAndWorkerId(id: Long, status: OperationStatus, workerId: String?) {
        operationDao.updateStatusAndWorkerId(id = id, status = status.name, workerId = workerId)
    }

    enum class OperationType {
        UPLOAD_FILE, UPLOAD_FOLDER, PASTE, DELETE
    }

    enum class OperationStatus {
        ENQUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED, WAITING_RESOLUTION
    }
}
