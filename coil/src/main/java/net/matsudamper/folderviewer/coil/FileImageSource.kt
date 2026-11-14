package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.common.FileObjectId

sealed interface FileImageSource {
    val fileId: FileObjectId.Item
    val cacheKey: String?

    data class Thumbnail(
        override val fileId: FileObjectId.Item,
        override val cacheKey: String?,
    ) : FileImageSource

    data class Original(
        override val fileId: FileObjectId.Item,
        override val cacheKey: String?,
    ) : FileImageSource
}
