package net.matsudamper.folderviewer.viewmodel.worker

import android.app.PendingIntent

interface OperationNotificationIntentFactory {
    fun createUploadDetailIntent(workerId: String): PendingIntent
    fun createPasteDetailIntent(jobId: Long): PendingIntent
    fun createDeleteDetailIntent(operationId: Long): PendingIntent
}
