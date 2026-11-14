package net.matsudamper.folderviewer.ui.upload

import androidx.compose.runtime.Immutable

data class UploadProgressUiState(
    val uploadItems: List<UploadItem>,
    val showClearConfirmDialog: Boolean,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBackClick()
        fun onClearHistoryClick()
        fun onClearHistoryConfirm()
        fun onClearHistoryDismiss()
    }

    sealed interface UploadItem {
        val key: String
        val name: String
        val description: String
        val state: UploadState
        val detailNavigationEnabled: Boolean
        val progress: Float?
        val progressText: String?
        val callbacks: ItemCallbacks

        data class File(
            override val key: String,
            override val name: String,
            override val description: String,
            override val state: UploadState,
            override val detailNavigationEnabled: Boolean,
            override val progress: Float?,
            override val progressText: String?,
            override val callbacks: ItemCallbacks,
        ) : UploadItem

        data class Folder(
            override val key: String,
            override val name: String,
            override val description: String,
            override val state: UploadState,
            override val detailNavigationEnabled: Boolean,
            override val progress: Float?,
            override val progressText: String?,
            override val callbacks: ItemCallbacks,
        ) : UploadItem

        data class Paste(
            override val key: String,
            override val name: String,
            override val description: String,
            override val state: UploadState,
            override val detailNavigationEnabled: Boolean,
            val currentFileProgress: Float?,
            override val progress: Float?,
            override val progressText: String?,
            override val callbacks: ItemCallbacks,
            val pauseButtonVisible: Boolean,
            val pausePendingIndicatorVisible: Boolean,
            val resumeButtonVisible: Boolean,
            val cancelButtonVisible: Boolean,
            val pasteCallbacks: PasteCallbacks,
        ) : UploadItem

        data class Delete(
            override val key: String,
            override val name: String,
            override val description: String,
            override val state: UploadState,
            override val detailNavigationEnabled: Boolean,
            override val progress: Float?,
            override val progressText: String?,
            override val callbacks: ItemCallbacks,
        ) : UploadItem

        data class Extract(
            override val key: String,
            override val name: String,
            override val description: String,
            override val state: UploadState,
            override val detailNavigationEnabled: Boolean,
            override val progress: Float?,
            override val progressText: String?,
            override val callbacks: ItemCallbacks,
        ) : UploadItem
    }

    @Immutable
    interface ItemCallbacks {
        fun onClick()
    }

    @Immutable
    interface PasteCallbacks {
        fun onPauseClick()
        fun onResumeClick()
        fun onCancelClick()
    }

    enum class UploadState {
        ENQUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        PAUSED,
        WAITING_RESOLUTION,
    }
}
