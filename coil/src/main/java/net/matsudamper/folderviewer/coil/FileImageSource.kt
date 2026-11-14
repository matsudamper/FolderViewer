package net.matsudamper.folderviewer.coil

import net.matsudamper.folderviewer.common.FileObjectId

sealed interface FileImageSource {
    val fileId: FileObjectId.Item

    /**
     * ファイルの内容が更新されると変わる値。null の場合は読み込み時に取得する
     */
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
