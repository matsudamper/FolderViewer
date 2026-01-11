package net.matsudamper.folderviewer.repository

import kotlinx.serialization.Serializable

@Serializable
sealed interface StorageConfiguration {
    val id: String
    val name: String

    @Serializable
    data class Smb(
        override val id: String,
        override val name: String,
        val ip: String,
        val username: String,
        val password: String,
    ) : StorageConfiguration

    @Serializable
    data class Local(
        override val id: String,
        override val name: String,
        val rootPath: String,
    ) : StorageConfiguration

    @Serializable
    data class SharePoint(
        override val id: String,
        override val name: String,
        val objectId: String,
        val tenantId: String,
        val clientId: String,
        val clientSecret: String,
    ) : StorageConfiguration
}
