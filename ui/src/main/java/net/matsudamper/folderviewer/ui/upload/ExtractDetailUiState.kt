package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class ExtractDetailUiState(
    val jobName: String,
    val statusText: String,
    val status: Status,
    val sourceFile: String,
    val outputName: String,
    val outputPath: String?,
    val extractTypeLabel: String,
    val errorMessage: String?,
    val errorCause: String?,
    val callbacks: Callbacks,
) {
    enum class Status {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    @Immutable
    interface Callbacks {
        fun onBackClick()
    }
}
