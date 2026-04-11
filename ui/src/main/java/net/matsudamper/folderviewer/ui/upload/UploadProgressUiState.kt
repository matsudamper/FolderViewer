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
        fun onItemClick(item: UploadItem)
        fun onClearHistoryClick()
        fun onClearHistoryConfirm()
        fun onClearHistoryDismiss()
    }

    sealed interface UploadItem {
        val id: String
        val name: String
        val state: UploadState
        val canNavigate: Boolean
        val progress: Float?
        val progressText: String?

        data class File(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
            override val progress: Float?,
            override val progressText: String?,
        ) : UploadItem

        data class Folder(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
            val fileCount: Int,
            override val progress: Float?,
            override val progressText: String?,
        ) : UploadItem

        data class Paste(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
            val mode: String,
            val operationMode: String,
            val totalFiles: Int,
            val completedFiles: Int,
            val failedFiles: Int,
            val duplicateFiles: Int,
            val firstFileName: String?,
            val currentFileName: String?,
            val currentFileProgress: Float?,
            override val progress: Float?,
            override val progressText: String?,
            val isPausable: Boolean,
            val isResumable: Boolean,
            val pasteCallbacks: PasteCallbacks,
        ) : UploadItem

        data class Delete(
            override val id: String,
            override val name: String,
            override val state: UploadState,
            override val canNavigate: Boolean,
            val totalFiles: Int,
            val completedFiles: Int,
            val failedFiles: Int,
            val firstFileName: String?,
            val currentFileName: String?,
            override val progress: Float?,
            override val progressText: String?,
        ) : UploadItem
    }

    @Immutable
    interface PasteCallbacks {
        fun onPauseClick()
        fun onResumeClick()
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
