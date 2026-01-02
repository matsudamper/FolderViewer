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
}

@Serializable
internal data class StorageList(
    val list: List<StorageConfiguration> = emptyList(),
)
