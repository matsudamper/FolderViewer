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
    val uploadFiles: List<UploadFile>,
    val callbacks: Callbacks,
) {
    enum class UploadStatus {
        UPLOADING,
        SUCCEEDED,
        FAILED,
    }

    @Immutable
    data class UploadFile(
        val name: String,
        val formattedSize: String?,
    )

    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onNavigateToDirectoryClick()
    }
}
