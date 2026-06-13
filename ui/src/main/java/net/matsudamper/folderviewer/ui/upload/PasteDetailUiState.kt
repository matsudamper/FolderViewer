package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class PasteDetailUiState(
    val jobName: String,
    val statusText: String,
    val status: Status,
    val errorMessage: String?,
    val errorCause: String?,
    val duplicateFiles: List<DuplicateFileItem>,
    val files: List<OperationFileRow>,
    val fileFilter: OperationFileFilter,
    val canRetry: Boolean,
    val canApply: Boolean,
    val progress: Float?,
    val fileCountText: String?,
    val sizeProgressText: String?,
    val currentFileName: String?,
    val currentFileProgress: Float?,
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
        val sourceThumbnail: FileImageSource.Thumbnail?,
        val destinationThumbnail: FileImageSource.Thumbnail?,
        val resolution: Resolution?,
        val onKeepDestination: () -> Unit,
        val onOverwriteWithSource: () -> Unit,
    )

    enum class Resolution {
        NONE,
        KEEP_DESTINATION,
        OVERWRITE_WITH_SOURCE,
    }

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onApplyResolutions()
        fun onRetryClick()
    }
}
