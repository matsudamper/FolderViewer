package net.matsudamper.folderviewer.ui.storage

import androidx.compose.runtime.Immutable

public data class SharePointAddUiState(
    val name: String,
    val siteUrl: String,
    val apiKey: String,
    val isEditMode: Boolean,
    val isLoading: Boolean,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onSave(input: SharePointInput)
        fun onBack()
    }
}
