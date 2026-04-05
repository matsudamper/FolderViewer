package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.common.FileObjectId

sealed interface FileImageSource {
    val fileId: FileObjectId.Item

    data class Thumbnail(
        override val fileId: FileObjectId.Item,
    ) : FileImageSource

    data class Original(
        override val fileId: FileObjectId.Item,
    ) : FileImageSource
}
