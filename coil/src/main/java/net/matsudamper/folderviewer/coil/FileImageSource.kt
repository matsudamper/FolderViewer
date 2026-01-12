package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.common.FileObjectId

sealed interface FileImageSource {
    val storageId: String
    val fileId: FileObjectId.Item

    data class Thumbnail(
        override val storageId: String,
        override val fileId: FileObjectId.Item,
    ) : FileImageSource

    data class Original(
        override val storageId: String,
        override val fileId: FileObjectId.Item,
    ) : FileImageSource
}
