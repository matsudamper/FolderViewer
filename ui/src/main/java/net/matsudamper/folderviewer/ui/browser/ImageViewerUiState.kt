package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class ImageViewerUiState(
    val images: List<ImageItem>,
    val currentIndex: Int,
    val callbacks: Callbacks,
) {
    data class ImageItem(
        val title: String,
        val imageSource: FileImageSource.Original,
    )

    @Immutable
    interface Callbacks {
        fun onBack()
        fun onImageChanged(index: Int)
    }
}
