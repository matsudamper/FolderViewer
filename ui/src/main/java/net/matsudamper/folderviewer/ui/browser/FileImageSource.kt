package net.matsudamper.folderviewer.ui.browser

public sealed interface FileImageSource {
    public val path: String

    public data class Thumbnail(override val path: String) : FileImageSource
    public data class Original(override val path: String) : FileImageSource
}
