package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId

sealed interface FileImageSource {
    val storageId: StorageId
    val fileId: FileObjectId.Item

    data class Thumbnail(
        override val storageId: StorageId,
        override val fileId: FileObjectId.Item,
    ) : FileImageSource

    data class Original(
        override val storageId: StorageId,
        override val fileId: FileObjectId.Item,
    ) : FileImageSource
}
