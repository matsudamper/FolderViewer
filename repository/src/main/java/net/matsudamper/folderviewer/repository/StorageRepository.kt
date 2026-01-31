package net.matsudamper.folderviewer.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.proto.FavoriteConfigurationProto
import net.matsudamper.folderviewer.repository.proto.LocalConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SecureStorageProto
import net.matsudamper.folderviewer.repository.proto.SharePointConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SmbConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageListProto

private const val DataStorageFileName = "storage_list.pb"

private val Context.dataStore: DataStore<StorageListProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = StorageListSerializer,
)

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val storageList: Flow<List<StorageConfiguration>> = context.dataStore.data
        .combine(context.secureDataStore.data) { proto, secureData ->
            proto.listList.mapNotNull { it.toDomain(secureData) }
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

        // パスワードを安全に保存する
        context.secureDataStore.updateData { currentSecure ->
            currentSecure.toBuilder()
                .putCredentials(id.id, config.password)
                .build()
        }

        // DataStoreを更新する
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

        context.secureDataStore.updateData { currentSecure ->
            currentSecure.toBuilder()
                .putCredentials(id.id, config.password)
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
            .mapNotNull { it.toDomain() as? StorageConfiguration.Local }
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

        context.secureDataStore.updateData { currentSecure ->
            currentSecure.toBuilder()
                .putCredentials("${id.id}_tenantId", config.tenantId)
                .putCredentials("${id.id}_clientId", config.clientId)
                .putCredentials("${id.id}_clientSecret", config.clientSecret)
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

        context.secureDataStore.updateData { currentSecure ->
            currentSecure.toBuilder()
                .putCredentials("${id.id}_tenantId", config.tenantId)
                .putCredentials("${id.id}_clientId", config.clientId)
                .putCredentials("${id.id}_clientSecret", config.clientSecret)
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
        context.dataStore.updateData { currentList ->
            val index = currentList.listList.indexOfFirst { it.id == id.id }
            if (index >= 0) {
                context.secureDataStore.updateData { currentSecure ->
                    currentSecure.toBuilder()
                        .removeCredentials(id.id)
                        .removeCredentials("${id.id}_tenantId")
                        .removeCredentials("${id.id}_clientId")
                        .removeCredentials("${id.id}_clientSecret")
                        .build()
                }
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
        val secureData = context.secureDataStore.data.first()
        val configProto = proto.listList.find { it.id == id.id } ?: return null

        return when (val config = configProto.toDomain(secureData)) {
            is StorageConfiguration.Smb -> SmbFileRepository(config)
            is StorageConfiguration.Local -> LocalFileRepository(config)
            is StorageConfiguration.SharePoint -> SharePointFileRepository(config)
            null -> null
        }
    }

    private fun StorageConfigurationProto.toDomain(secureData: SecureStorageProto): StorageConfiguration? {
        return when (configCase) {
            StorageConfigurationProto.ConfigCase.SMB -> {
                StorageConfiguration.Smb(
                    id = StorageId(id),
                    name = name,
                    ip = smb.ip,
                    username = smb.username,
                    password = secureData.credentialsMap[id].orEmpty(),
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
                StorageConfiguration.SharePoint(
                    id = StorageId(id),
                    name = name,
                    objectId = sharepoint.objectId,
                    tenantId = secureData.credentialsMap["${id}_tenantId"].orEmpty(),
                    clientId = secureData.credentialsMap["${id}_clientId"].orEmpty(),
                    clientSecret = secureData.credentialsMap["${id}_clientSecret"].orEmpty(),
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

internal object StorageListSerializer : Serializer<StorageListProto> {
    override val defaultValue: StorageListProto = StorageListProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): StorageListProto {
        @Suppress("TooGenericExceptionCaught")
        try {
            return StorageListProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        } catch (exception: Exception) {
            throw CorruptionException("Unexpected error reading proto.", exception)
        }
    }

    override suspend fun writeTo(t: StorageListProto, output: OutputStream) {
        t.writeTo(output)
    }
}
