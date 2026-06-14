package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class DeleteDetailUiState(
    val jobName: String,
    val statusText: String,
    val status: Status,
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val errorMessage: String?,
    val errorCause: String?,
    val files: List<OperationFileRow>,
    val fileFilter: OperationFileFilter,
    val canRetry: Boolean,
    val callbacks: Callbacks,
) {
    enum class Status {
        RUNNING,
        COMPLETED,
        FAILED,
    }

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onRetryClick()
    }
}
