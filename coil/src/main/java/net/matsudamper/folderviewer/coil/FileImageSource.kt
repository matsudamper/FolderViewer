package net.matsudamper.folderviewer.coil

sealed interface FileImageSource {
    val storageId: String
    val path: String

    data class Thumbnail(
        override val storageId: String,
        override val path: String,
    ) : FileImageSource

    data class Original(
        override val storageId: String,
        override val path: String,
    ) : FileImageSource
}
