package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.repository.FileItem

sealed interface FileImageSource {
    val fileItem: FileItem

    data class Thumbnail(override val fileItem: FileItem) : FileImageSource
    data class Original(override val fileItem: FileItem) : FileImageSource
}
