package net.matsudamper.folderviewer.ui.storage

import androidx.compose.runtime.Immutable

public data class SmbAddUiState(
    val name: String,
    val ip: String,
    val username: String,
    val password: String,
    val isEditMode: Boolean,
    val isLoading: Boolean,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onSave(input: SmbInput)
        fun onBack()
    }
}
