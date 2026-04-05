package net.matsudamper.folderviewer.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.proto.FavoriteConfigurationProto
import net.matsudamper.folderviewer.repository.proto.LocalConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SharePointConfigurationProto
import net.matsudamper.folderviewer.repository.proto.SmbConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageListProto
import net.matsudamper.folderviewer.repository.proto.StorageListSerializer

private const val DataStorageFileName = "storage_list.pb"

private val Context.dataStore: DataStore<StorageListProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = StorageListSerializer,
)

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val storageList: Flow<List<StorageConfiguration>> =
        context.dataStore.data.map { storageProto ->
            storageProto.listList.mapNotNull { it.toDomain() }
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
                currentList.toBuilder()
                    .removeList(index)
                    .build()
            } else {
                currentList
            }
        }
    }

    suspend fun addFavorite(fileId: FileObjectId.Item, displayPath: String, name: String) {
        val id = UUID.randomUUID().toString()
        val config = FavoriteConfiguration(
            id = id,
            name = name,
            path = fileId.id,
            fileId = fileId,
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

    suspend fun isRootWritable(fileObjectId: FileObjectId): Boolean {
        if (fileObjectId is FileObjectId.Item) return true
        val proto = context.dataStore.data.first()
        val configProto = proto.listList.find { it.id == fileObjectId.storageId.id } ?: return false
        return when (configProto.toDomain()) {
            is StorageConfiguration.Smb -> false
            is StorageConfiguration.Local -> true
            is StorageConfiguration.SharePoint -> true
            null -> false
        }
    }

    suspend fun getFileRepository(id: StorageId): FileRepository? {
        val proto = context.dataStore.data.first()
        val configProto = proto.listList.find { it.id == id.id } ?: return null

        return when (val config = configProto.toDomain()) {
            is StorageConfiguration.Smb -> SmbFileRepository(config)
            is StorageConfiguration.Local -> LocalFileRepository(config)
            is StorageConfiguration.SharePoint -> SharePointFileRepository(config)
            null -> null
        }
    }

    private fun StorageConfigurationProto.toDomain(): StorageConfiguration? {
        return when (configCase) {
            StorageConfigurationProto.ConfigCase.SMB -> {
                StorageConfiguration.Smb(
                    id = StorageId(id),
                    name = name,
                    ip = smb.ip,
                    username = smb.username,
                    password = smb.password,
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
                    tenantId = sharepoint.tenantId,
                    clientId = sharepoint.clientId,
                    clientSecret = sharepoint.clientSecret,
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
                    .setPassword(password)
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
                    .setTenantId(tenantId)
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .build(),
            )
            .build()
    }

    private fun FavoriteConfigurationProto.toDomain(): FavoriteConfiguration {
        val fileIdString = if (fileId.isNotEmpty()) fileId else path
        return FavoriteConfiguration(
            id = id,
            name = name,
            path = fileIdString,
            fileId = FileObjectId.Item(storageId = StorageId(storageId), id = fileIdString),
            displayPath = if (displayPath.isNotEmpty()) displayPath else path,
        )
    }

    private fun FavoriteConfiguration.toProto(): FavoriteConfigurationProto {
        return FavoriteConfigurationProto.newBuilder()
            .setId(id)
            .setName(name)
            .setStorageId(fileId.storageId.id)
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
