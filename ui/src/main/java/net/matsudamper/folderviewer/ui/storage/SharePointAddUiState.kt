package net.matsudamper.folderviewer.ui.storage

import androidx.compose.runtime.Immutable

public data class SharePointAddUiState(
    val name: String,
    val objectId: String,
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
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
