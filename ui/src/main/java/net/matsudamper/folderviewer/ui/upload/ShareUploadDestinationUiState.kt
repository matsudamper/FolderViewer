package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class ShareUploadDestinationUiState(
    val title: String,
    val pendingCount: Int,
    val canUpload: Boolean,
    val canCreateDirectory: Boolean,
    val isRefreshing: Boolean,
    val contentState: ContentState,
    val callbacks: Callbacks,
) {
    sealed interface ContentState {
        data object Loading : ContentState
        data object Error : ContentState
        data object Empty : ContentState
        data class Content(val folders: List<Folder>) : ContentState
    }

    @Immutable
    data class Folder(
        val name: String,
        val key: String,
        val callbacks: Callbacks,
    ) {
        @Immutable
        interface Callbacks {
            fun onClick()
        }
    }

    @Immutable
    interface Callbacks {
        fun onBack()
        fun onRefresh()
        fun onUploadHere()
        fun onCreateDirectory(name: String)
    }
}
