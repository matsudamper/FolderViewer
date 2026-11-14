package net.matsudamper.folderviewer.ui.picker

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class ExternalFilePickerSelectedListUiState(
    val items: List<Item>,
    val callbacks: Callbacks,
) {
    data class Item(
        val key: String,
        val name: String,
        val thumbnail: FileImageSource.Thumbnail?,
        val isPreviewable: Boolean,
        val callbacks: ItemCallbacks,
    ) {
        @Immutable
        interface ItemCallbacks {
            fun onRemove()
            fun onTap()
        }
    }

    @Immutable
    interface Callbacks {
        fun onBack()
    }
}
