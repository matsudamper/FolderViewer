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
    val failedFileItems: List<FailedFileItem>,
    val completedFileItems: List<CompletedFileItem>,
    val callbacks: Callbacks,
) {
    enum class Status {
        RUNNING,
        COMPLETED,
        FAILED,
    }

    data class FailedFileItem(
        val fileName: String,
        val path: String,
        val errorMessage: String,
    )

    data class CompletedFileItem(
        val fileName: String,
        val path: String,
    )

    @Immutable
    interface Callbacks {
        fun onBackClick()
    }
}
