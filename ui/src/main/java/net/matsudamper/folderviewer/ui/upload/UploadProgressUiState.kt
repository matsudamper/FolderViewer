package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class UploadProgressUiState(
    val uploadItems: List<UploadItem>,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onItemClick(item: UploadItem)
    }

    sealed interface UploadItem {
        val id: String
        val name: String
        val state: UploadState
        val canNavigate: Boolean

        data class File(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
        ) : UploadItem

        data class Folder(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
            val fileCount: Int,
        ) : UploadItem
    }

    enum class UploadState {
        ENQUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
    }
}
