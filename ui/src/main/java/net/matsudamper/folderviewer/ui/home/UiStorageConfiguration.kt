package net.matsudamper.folderviewer.ui.home

import net.matsudamper.folderviewer.common.StorageId

public sealed interface UiStorageConfiguration {
    public val id: StorageId
    public val name: String

    public data class Smb(
        override val id: StorageId,
        override val name: String,
        val ip: String,
        val username: String,
    ) : UiStorageConfiguration

    public data class Local(
        override val id: StorageId,
        override val name: String,
        val rootPath: String,
    ) : UiStorageConfiguration

    public data class SharePoint(
        override val id: StorageId,
        override val name: String,
        val objectId: String,
    ) : UiStorageConfiguration
}
