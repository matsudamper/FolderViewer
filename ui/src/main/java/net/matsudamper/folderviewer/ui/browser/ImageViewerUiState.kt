package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class ImageViewerUiState(
    val title: String,
    val imageSource: FileImageSource.Original,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onBack()
    }
}
