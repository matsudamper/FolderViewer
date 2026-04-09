package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class PasteDetailUiState(
    val jobName: String,
    val statusText: String,
    val status: Status,
    val errorMessage: String?,
    val errorCause: String?,
    val duplicateFiles: List<DuplicateFileItem>,
    val completedFiles: List<CompletedFileItem>,
    val failedFiles: List<FailedFileItem> = emptyList(),
    val canApply: Boolean,
    val callbacks: Callbacks,
) {
    enum class Status {
        ENQUEUED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        WAITING_RESOLUTION,
    }

    data class DuplicateFileItem(
        val fileId: Long,
        val fileName: String,
        val sourcePath: String,
        val sourceSize: Long,
        val sourceSizeText: String,
        val destinationPath: String,
        val destinationSize: Long,
        val destinationSizeText: String,
        val resolution: Resolution?,
        val onKeepDestination: () -> Unit,
        val onOverwriteWithSource: () -> Unit,
    )

    enum class Resolution {
        NONE,
        KEEP_DESTINATION,
        OVERWRITE_WITH_SOURCE,
    }

    data class CompletedFileItem(
        val fileName: String,
        val path: String,
        val sizeText: String,
        val resolution: Resolution,
    )

    data class FailedFileItem(
        val fileName: String,
        val path: String,
        val errorMessage: String,
    )

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onApplyResolutions()
    }
}
