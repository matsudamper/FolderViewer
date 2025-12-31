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
        // パスワードはDataStoreに平文で保存すべきではない。
        // IDをキーとしてEncryptedSharedPreferencesに保存する。
    ) : StorageConfiguration

    @Serializable
    data class Sftp(
        override val id: String,
        override val name: String,
        val host: String,
        val port: Int,
        val username: String,
        // パスワードはDataStoreに平文で保存すべきではない。
        // IDをキーとしてEncryptedSharedPreferencesに保存する。
    ) : StorageConfiguration
}

@Serializable
internal data class StorageList(
    val list: List<StorageConfiguration> = emptyList(),
)
