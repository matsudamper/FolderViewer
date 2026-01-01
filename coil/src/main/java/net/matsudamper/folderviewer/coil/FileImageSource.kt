package net.matsudamper.folderviewer.coil

sealed interface FileImageSource {
    val path: String

    data class Thumbnail(override val path: String) : FileImageSource
    data class Original(override val path: String) : FileImageSource
}
