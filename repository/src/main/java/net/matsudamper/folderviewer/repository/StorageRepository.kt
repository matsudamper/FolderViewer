package net.matsudamper.folderviewer.repository

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import net.matsudamper.folderviewer.repository.proto.SmbConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageConfigurationProto
import net.matsudamper.folderviewer.repository.proto.StorageListProto

private const val DataStorageFileName = "storage_list.pb"
private const val SecurePrefFileName = "secure_storage_creds"

private val Context.dataStore: DataStore<StorageListProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = StorageListSerializer,
)

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        SecurePrefFileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val storageList: Flow<List<StorageConfiguration>> = context.dataStore.data
        .map { proto ->
            proto.listList.mapNotNull { it.toDomain() }
        }

    suspend fun addSmbStorage(config: SmbStorageInput) {
        val id = UUID.randomUUID().toString()
        val smbConfig = StorageConfiguration.Smb(
            id = id,
            name = config.name,
            ip = config.ip,
            username = config.username,
        )

        // パスワードを安全に保存する
        sharedPreferences.edit {
            putString(id, config.password)
        }

        // DataStoreを更新する
        context.dataStore.updateData { currentList ->
            currentList.toBuilder()
                .addList(smbConfig.toProto())
                .build()
        }
    }

    suspend fun updateSmbStorage(id: String, config: SmbStorageInput) {
        val smbConfig = StorageConfiguration.Smb(
            id = id,
            name = config.name,
            ip = config.ip,
            username = config.username,
        )

        sharedPreferences.edit {
            putString(id, config.password)
        }

        context.dataStore.updateData { currentList ->
            val index = currentList.listList.indexOfFirst { it.id == id }
            if (index >= 0) {
                currentList.toBuilder()
                    .setList(index, smbConfig.toProto())
                    .build()
            } else {
                currentList
            }
        }
    }

    fun getPassword(id: String): String? {
        return sharedPreferences.getString(id, null)
    }

    suspend fun getFileRepository(id: String): FileRepository? {
        val proto = context.dataStore.data.first()
        val configProto = proto.listList.find { it.id == id } ?: return null
        val config = configProto.toDomain() as? StorageConfiguration.Smb ?: return null
        val password = getPassword(id) ?: return null
        return SmbFileRepository(config, password)
    }

    private fun StorageConfigurationProto.toDomain(): StorageConfiguration? {
        return when (configCase) {
            StorageConfigurationProto.ConfigCase.SMB -> {
                StorageConfiguration.Smb(
                    id = id,
                    name = name,
                    ip = smb.ip,
                    username = smb.username,
                )
            }

            StorageConfigurationProto.ConfigCase.CONFIG_NOT_SET, null -> null
        }
    }

    private fun StorageConfiguration.Smb.toProto(): StorageConfigurationProto {
        return StorageConfigurationProto.newBuilder()
            .setId(id)
            .setName(name)
            .setSmb(
                SmbConfigurationProto.newBuilder()
                    .setIp(ip)
                    .setUsername(username)
                    .build(),
            )
            .build()
    }

    data class SmbStorageInput(
        val name: String,
        val ip: String,
        val username: String,
        val password: String,
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
