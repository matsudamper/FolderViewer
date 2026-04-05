package net.matsudamper.folderviewer.repository

import kotlinx.serialization.Serializable
import net.matsudamper.folderviewer.common.StorageId

@Serializable
sealed interface StorageConfiguration {
    val id: StorageId
    val name: String

    @Serializable
    data class Smb(
        override val id: StorageId,
        override val name: String,
        val ip: String,
        val username: String,
        val password: String,
    ) : StorageConfiguration

    @Serializable
    data class Local(
        override val id: StorageId,
        override val name: String,
        val rootPath: String,
    ) : StorageConfiguration

    @Serializable
    data class SharePoint(
        override val id: StorageId,
        override val name: String,
        val objectId: String,
        val tenantId: String,
        val clientId: String,
        val clientSecret: String,
    ) : StorageConfiguration
}
