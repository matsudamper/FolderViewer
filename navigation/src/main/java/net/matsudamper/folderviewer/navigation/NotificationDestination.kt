package net.matsudamper.folderviewer.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey

object NotificationDestination {
    private const val ExtraType = "net.matsudamper.folderviewer.notification.destination_type"
    private const val ExtraPasteJobId = "net.matsudamper.folderviewer.notification.paste_job_id"
    private const val ExtraDeleteOperationId = "net.matsudamper.folderviewer.notification.delete_operation_id"
    private const val ExtraUploadWorkerId = "net.matsudamper.folderviewer.notification.upload_worker_id"
    private const val TypePasteDetail = "paste_detail"
    private const val TypeDeleteDetail = "delete_detail"
    private const val TypeUploadDetail = "upload_detail"

    fun putExtras(intent: Intent, destination: NavKey) {
        when (destination) {
            is PasteDetail -> {
                intent.putExtra(ExtraType, TypePasteDetail)
                intent.putExtra(ExtraPasteJobId, destination.jobId)
            }

            is DeleteDetail -> {
                intent.putExtra(ExtraType, TypeDeleteDetail)
                intent.putExtra(ExtraDeleteOperationId, destination.operationId)
            }

            is UploadDetail -> {
                intent.putExtra(ExtraType, TypeUploadDetail)
                intent.putExtra(ExtraUploadWorkerId, destination.workerId)
            }

            else -> Unit
        }
    }

    fun fromIntent(intent: Intent): NavKey? {
        return when (intent.getStringExtra(ExtraType)) {
            TypePasteDetail -> {
                val jobId = intent.getLongExtra(ExtraPasteJobId, -1L)
                if (jobId == -1L) null else PasteDetail(jobId = jobId)
            }

            TypeDeleteDetail -> {
                val operationId = intent.getLongExtra(ExtraDeleteOperationId, -1L)
                if (operationId == -1L) null else DeleteDetail(operationId = operationId)
            }

            TypeUploadDetail -> {
                val workerId = intent.getStringExtra(ExtraUploadWorkerId)
                if (workerId == null) null else UploadDetail(workerId = workerId)
            }

            else -> null
        }
    }
}
