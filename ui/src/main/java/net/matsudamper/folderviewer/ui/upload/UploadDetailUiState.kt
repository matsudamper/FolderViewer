package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class UploadDetailUiState(
    val name: String,
    val isFolder: Boolean,
    val displayPath: String,
    val storageName: String,
    val uploadStatus: UploadStatus,
    val errorMessage: String?,
    val errorCause: String?,
    val progressText: String?,
    val currentUploadFile: CurrentUploadFile?,
    val callbacks: Callbacks,
) {
    enum class UploadStatus {
        UPLOADING,
        SUCCEEDED,
        FAILED,
    }

    @Immutable
    data class CurrentUploadFile(
        val name: String,
        val progressText: String?,
        val progress: Float?,
    )

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onNavigateToDirectoryClick()
    }
}
