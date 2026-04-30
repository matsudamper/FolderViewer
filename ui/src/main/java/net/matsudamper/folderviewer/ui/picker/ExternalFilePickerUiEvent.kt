package net.matsudamper.folderviewer.ui.picker

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.common.FileObjectId

sealed interface ExternalFilePickerUiEvent {
    data class ShowFilePropertiesDialog(
        val fileId: FileObjectId.Item,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val isPreviewable: Boolean,
        val callbacks: Callbacks,
    ) : ExternalFilePickerUiEvent {
        @Immutable
        interface Callbacks {
            fun onPreview()
        }
    }
}
