package net.matsudamper.folderviewer.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.proto.EncryptedCredentialsSerializer
import net.matsudamper.folderviewer.repository.proto.FavoriteConfigurationProto
import net.matsudamper.folderviewer.repository.proto.LocalConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SecureCredentialsProto
import net.matsudamper.folderviewer.repository.proto.SharePointConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SharePointCredentialProto
import net.matsudamper.folderviewer.repository.proto.SmbConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageListProto
import net.matsudamper.folderviewer.repository.proto.StorageListSerializer

private const val DataStorageFileName = "storage_list.pb"
private const val CredentialsDataStoreFileName = "secure_credentials.pb"
private const val SecurePrefFileName = "secure_storage_creds"

private val Context.dataStore: DataStore<StorageListProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = StorageListSerializer,
)

private val Context.credentialsDataStore: DataStore<SecureCredentialsProto> by dataStore(
    fileName = CredentialsDataStoreFileName,
    serializer = EncryptedCredentialsSerializer,
)

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    init {
        CoroutineScope(Dispatchers.IO).launch {
            migrateFromEncryptedSharedPreferences()
        }
    }

    val storageList: Flow<List<StorageConfiguration>> =
        context.dataStore.data.combine(context.credentialsDataStore.data) { storageProto, credentialsProto ->
            storageProto.listList.mapNotNull { it.toDomain(credentialsProto) }
        }

    val favorites: Flow<List<FavoriteConfiguration>> = context.dataStore.data
        .map { proto ->
            proto.favoritesList.map { it.toDomain() }
        }

    suspend fun addSmbStorage(config: SmbStorageInput) {
        val id = StorageId(UUID.randomUUID().toString())
        val smbConfig = StorageConfiguration.Smb(
            id = id,
            name = config.name,
            ip = config.ip,
            username = config.username,
            password = config.password,
        )

        context.credentialsDataStore.updateData { current ->
            current.toBuilder()
                .putSmbPasswords(id.id, config.password)
                .build()
        }

        context.dataStore.updateData { currentList ->
            currentList.toBuilder()
                .addList(smbConfig.toProto())
                .build()
        }
    }

    suspend fun updateSmbStorage(id: StorageId, config: SmbStorageInput) {
        val smbConfig = StorageConfiguration.Smb(
            id = id,
            name = config.name,
            ip = config.ip,
            username = config.username,
            password = config.password,
        )

        context.credentialsDataStore.updateData { current ->
            current.toBuilder()
                .putSmbPasswords(id.id, config.password)
                .build()
        }

        context.dataStore.updateData { currentList ->
            val index = currentList.listList.indexOfFirst { it.id == id.id }
            if (index >= 0) {
                currentList.toBuilder()
                    .setList(index, smbConfig.toProto())
                    .build()
            } else {
                currentList
            }
        }
    }

    suspend fun detectLocalStorages() {
        val existingStorages = context.dataStore.data.first().listList
            .mapNotNull { it.toDomain(SecureCredentialsProto.getDefaultInstance()) as? StorageConfiguration.Local }
            .map { it.rootPath }
            .toSet()

        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage.exists() && primaryStorage.canRead()) {
            val primaryPath = primaryStorage.absolutePath
            if (primaryPath !in existingStorages) {
                val localConfig = StorageConfiguration.Local(
                    id = StorageId(UUID.randomUUID().toString()),
                    name = "ローカルストレージ",
                    rootPath = primaryPath,
                )

                context.dataStore.updateData { currentList ->
                    currentList.toBuilder()
                        .addList(localConfig.toProto())
                        .build()
                }
            }
        }
    }

    suspend fun addLocalStorage(name: String, rootPath: String) {
        val id = StorageId(UUID.randomUUID().toString())
        val localConfig = StorageConfiguration.Local(
            id = id,
            name = name,
            rootPath = rootPath,
        )

        context.dataStore.updateData { currentList ->
            currentList.toBuilder()
                .addList(localConfig.toProto())
                .build()
        }
    }

    suspend fun addSharePointStorage(config: SharePointStorageInput) {
        val id = StorageId(UUID.randomUUID().toString())
        val sharePointConfig = StorageConfiguration.SharePoint(
            id = id,
            name = config.name,
            objectId = config.objectId,
            tenantId = config.tenantId,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
        )

        context.credentialsDataStore.updateData { current ->
            current.toBuilder()
                .putSharePointCredentials(
                    id.id,
                    SharePointCredentialProto.newBuilder()
                        .setTenantId(config.tenantId)
                        .setClientId(config.clientId)
                        .setClientSecret(config.clientSecret)
                        .build(),
                )
                .build()
        }

        context.dataStore.updateData { currentList ->
            currentList.toBuilder()
                .addList(sharePointConfig.toProto())
                .build()
        }
    }

    suspend fun updateSharePointStorage(id: StorageId, config: SharePointStorageInput) {
        val sharePointConfig = StorageConfiguration.SharePoint(
            id = id,
            name = config.name,
            objectId = config.objectId,
            tenantId = config.tenantId,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
        )

        context.credentialsDataStore.updateData { current ->
            current.toBuilder()
                .putSharePointCredentials(
                    id.id,
                    SharePointCredentialProto.newBuilder()
                        .setTenantId(config.tenantId)
                        .setClientId(config.clientId)
                        .setClientSecret(config.clientSecret)
                        .build(),
                )
                .build()
        }

        context.dataStore.updateData { currentList ->
            val index = currentList.listList.indexOfFirst { it.id == id.id }
            if (index >= 0) {
                currentList.toBuilder()
                    .setList(index, sharePointConfig.toProto())
                    .build()
            } else {
                currentList
            }
        }
    }

    suspend fun deleteStorage(id: StorageId) {
        context.credentialsDataStore.updateData { current ->
            current.toBuilder()
                .removeSmbPasswords(id.id)
                .removeSharePointCredentials(id.id)
                .build()
        }

        context.dataStore.updateData { currentList ->
            val index = currentList.listList.indexOfFirst { it.id == id.id }
            if (index >= 0) {
                currentList.toBuilder()
                    .removeList(index)
                    .build()
            } else {
                currentList
            }
        }
    }

    suspend fun addFavorite(storageId: StorageId, fileId: String, displayPath: String, name: String) {
        val id = UUID.randomUUID().toString()
        val config = FavoriteConfiguration(
            id = id,
            name = name,
            storageId = storageId,
            path = fileId,
            fileId = FileObjectId.Item(fileId),
            displayPath = displayPath,
        )

        context.dataStore.updateData { currentList ->
            currentList.toBuilder()
                .addFavorites(config.toProto())
                .build()
        }
    }

    suspend fun removeFavorite(id: String) {
        context.dataStore.updateData { currentList ->
            val index = currentList.favoritesList.indexOfFirst { it.id == id }
            if (index >= 0) {
                currentList.toBuilder()
                    .removeFavorites(index)
                    .build()
            } else {
                currentList
            }
        }
    }

    suspend fun getFileRepository(id: StorageId): FileRepository? {
        val proto = context.dataStore.data.first()
        val credentials = context.credentialsDataStore.data.first()
        val configProto = proto.listList.find { it.id == id.id } ?: return null

        return when (val config = configProto.toDomain(credentials)) {
            is StorageConfiguration.Smb -> SmbFileRepository(config)
            is StorageConfiguration.Local -> LocalFileRepository(config)
            is StorageConfiguration.SharePoint -> SharePointFileRepository(config)
            null -> null
        }
    }

    private suspend fun migrateFromEncryptedSharedPreferences() {
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$SecurePrefFileName.xml")
        if (!prefsFile.exists()) return

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            SecurePrefFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val storageConfigs = context.dataStore.data.first().listList

        context.credentialsDataStore.updateData { current ->
            val builder = current.toBuilder()
            for (config in storageConfigs) {
                when (config.configCase) {
                    StorageConfigurationProto.ConfigCase.SMB -> {
                        val password = sharedPreferences.getString(config.id, null).orEmpty()
                        if (password.isNotEmpty()) {
                            builder.putSmbPasswords(config.id, password)
                        }
                    }

                    StorageConfigurationProto.ConfigCase.SHAREPOINT -> {
                        val tenantId = sharedPreferences.getString("${config.id}_tenantId", null).orEmpty()
                        val clientId = sharedPreferences.getString("${config.id}_clientId", null).orEmpty()
                        val clientSecret = sharedPreferences.getString("${config.id}_clientSecret", null).orEmpty()
                        if (tenantId.isNotEmpty() || clientId.isNotEmpty() || clientSecret.isNotEmpty()) {
                            builder.putSharePointCredentials(
                                config.id,
                                SharePointCredentialProto.newBuilder()
                                    .setTenantId(tenantId)
                                    .setClientId(clientId)
                                    .setClientSecret(clientSecret)
                                    .build(),
                            )
                        }
                    }

                    StorageConfigurationProto.ConfigCase.LOCAL,
                    StorageConfigurationProto.ConfigCase.CONFIG_NOT_SET,
                    null,
                    -> Unit
                }
            }
            builder.build()
        }

        context.deleteSharedPreferences(SecurePrefFileName)
    }

    private fun StorageConfigurationProto.toDomain(
        credentials: SecureCredentialsProto,
    ): StorageConfiguration? {
        return when (configCase) {
            StorageConfigurationProto.ConfigCase.SMB -> {
                StorageConfiguration.Smb(
                    id = StorageId(id),
                    name = name,
                    ip = smb.ip,
                    username = smb.username,
                    password = credentials.smbPasswordsMap[id].orEmpty(),
                )
            }

            StorageConfigurationProto.ConfigCase.LOCAL -> {
                StorageConfiguration.Local(
                    id = StorageId(id),
                    name = name,
                    rootPath = local.rootPath,
                )
            }

            StorageConfigurationProto.ConfigCase.SHAREPOINT -> {
                val credential = credentials.sharePointCredentialsMap[id]
                StorageConfiguration.SharePoint(
                    id = StorageId(id),
                    name = name,
                    objectId = sharepoint.objectId,
                    tenantId = credential?.tenantId.orEmpty(),
                    clientId = credential?.clientId.orEmpty(),
                    clientSecret = credential?.clientSecret.orEmpty(),
                )
            }

            StorageConfigurationProto.ConfigCase.CONFIG_NOT_SET, null -> null
        }
    }

    private fun StorageConfiguration.Smb.toProto(): StorageConfigurationProto {
        return StorageConfigurationProto.newBuilder()
            .setId(id.id)
            .setName(name)
            .setSmb(
                SmbConfigurationProto.newBuilder()
                    .setIp(ip)
                    .setUsername(username)
                    .build(),
            )
            .build()
    }

    private fun StorageConfiguration.Local.toProto(): StorageConfigurationProto {
        return StorageConfigurationProto.newBuilder()
            .setId(id.id)
            .setName(name)
            .setLocal(
                LocalConfigurationProto.newBuilder()
                    .setRootPath(rootPath)
                    .build(),
            )
            .build()
    }

    private fun StorageConfiguration.SharePoint.toProto(): StorageConfigurationProto {
        return StorageConfigurationProto.newBuilder()
            .setId(id.id)
            .setName(name)
            .setSharepoint(
                SharePointConfigurationProto.newBuilder()
                    .setObjectId(objectId)
                    .build(),
            )
            .build()
    }

    private fun FavoriteConfigurationProto.toDomain(): FavoriteConfiguration {
        val fileIdString = if (fileId.isNotEmpty()) fileId else path
        return FavoriteConfiguration(
            id = id,
            name = name,
            storageId = StorageId(storageId),
            path = fileIdString,
            fileId = FileObjectId.Item(fileIdString),
            displayPath = if (displayPath.isNotEmpty()) displayPath else path,
        )
    }

    private fun FavoriteConfiguration.toProto(): FavoriteConfigurationProto {
        return FavoriteConfigurationProto.newBuilder()
            .setId(id)
            .setName(name)
            .setStorageId(storageId.id)
            .setPath(path)
            .setFileId(fileId.id)
            .setDisplayPath(displayPath)
            .build()
    }

    data class SmbStorageInput(
        val name: String,
        val ip: String,
        val username: String,
        val password: String,
    )

    data class SharePointStorageInput(
        val name: String,
        val objectId: String,
        val tenantId: String,
        val clientId: String,
        val clientSecret: String,
    )
}
