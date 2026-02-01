package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class UploadDetailUiState(
    val name: String,
    val isFolder: Boolean,
    val displayPath: String,
    val storageName: String,
    val hasError: Boolean,
    val errorMessage: String?,
    val errorCause: String?,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onNavigateToDirectoryClick()
    }
}
