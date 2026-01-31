package net.matsudamper.folderviewer.repository

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.protobuf.InvalidProtocolBufferException
import net.matsudamper.folderviewer.repository.proto.SecureStorageProto
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SecureDataStorageFileName = "secure_storage.pb"
private const val MasterKeysetName = "master_keyset"
private const val MasterKeyPreference = "master_key_preference"
private const val MasterKeyUri = "android-keystore://master_key"

internal val Context.secureDataStore: DataStore<SecureStorageProto> by dataStore(
    fileName = SecureDataStorageFileName,
    serializer = SecureStorageSerializer(this),
)

internal class SecureStorageSerializer(
    private val context: Context,
) : Serializer<SecureStorageProto> {
    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AeadConfig.register()
        val keysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, MasterKeysetName, MasterKeyPreference)
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri(MasterKeyUri)
            .build()
            .keysetHandle
        keysetHandle.getPrimitive(Aead::class.java)
    }

    override val defaultValue: SecureStorageProto = SecureStorageProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): SecureStorageProto = withContext(Dispatchers.IO) {
        try {
            val encryptedBytes = input.readBytes()
            if (encryptedBytes.isEmpty()) {
                return@withContext defaultValue
            }
            val decryptedBytes = aead.decrypt(encryptedBytes, null)
            SecureStorageProto.parseFrom(decryptedBytes)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        } catch (exception: Exception) {
            throw CorruptionException("Unexpected error reading proto.", exception)
        }
    }

    override suspend fun writeTo(t: SecureStorageProto, output: OutputStream) = withContext(Dispatchers.IO) {
        val bytes = t.toByteArray()
        val encryptedBytes = aead.encrypt(bytes, null)
        output.write(encryptedBytes)
    }
}
