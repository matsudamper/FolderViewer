package net.matsudamper.folderviewer.ui.storage

data class SmbAddUiState(
    val name: String = "",
    val ip: String = "",
    val username: String = "",
    val password: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
)
