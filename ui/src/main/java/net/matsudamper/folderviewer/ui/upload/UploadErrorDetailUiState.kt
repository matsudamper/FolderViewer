package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class UploadErrorDetailUiState(
    val name: String,
    val isFolder: Boolean,
    val displayPath: String,
    val storageName: String,
    val errorMessage: String?,
    val errorCause: String?,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBackClick()
    }
}
