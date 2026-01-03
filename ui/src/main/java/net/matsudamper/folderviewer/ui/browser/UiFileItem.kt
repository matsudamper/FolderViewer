package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable
import net.matsudamper.folderviewer.coil.FileImageSource

data class UiFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val imageSource: FileImageSource.Thumbnail?,
    val callbacks: Callbacks,
) {
    @Immutable
    fun interface Callbacks {
        fun onClick()
    }
}
