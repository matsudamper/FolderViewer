package net.matsudamper.folderviewer.ui.browser

import net.matsudamper.folderviewer.coil.FileImageSource

data class UiFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val imageSource: FileImageSource.Thumbnail?,
)
