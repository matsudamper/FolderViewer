package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class PasteDetailUiState(
    val jobName: String,
    val statusText: String,
    val duplicateFiles: List<DuplicateFileItem>,
    val completedFiles: List<CompletedFileItem>,
    val canApply: Boolean,
    val callbacks: Callbacks,
) {
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
        KEEP_DESTINATION,
        OVERWRITE_WITH_SOURCE,
    }

    data class CompletedFileItem(
        val fileName: String,
        val path: String,
        val sizeText: String,
    )

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onApplyResolutions()
    }
}
