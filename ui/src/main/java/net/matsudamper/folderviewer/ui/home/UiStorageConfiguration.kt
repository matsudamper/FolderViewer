package net.matsudamper.folderviewer.ui.home

public sealed interface UiStorageConfiguration {
    public val id: String
    public val name: String

    public data class Smb(
        override val id: String,
        override val name: String,
        val ip: String,
        val username: String,
    ) : UiStorageConfiguration

    public data class Local(
        override val id: String,
        override val name: String,
        val rootPath: String,
    ) : UiStorageConfiguration

    public data class SharePoint(
        override val id: String,
        override val name: String,
        val objectId: String,
    ) : UiStorageConfiguration
}
