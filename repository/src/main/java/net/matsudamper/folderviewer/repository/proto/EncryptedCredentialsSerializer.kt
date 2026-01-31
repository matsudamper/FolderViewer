package net.matsudamper.folderviewer.repository.proto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.google.protobuf.InvalidProtocolBufferException
import java.security.GeneralSecurityException

private const val KeyAlias = "secure_credentials_key"
private const val GcmIvLength = 12
private const val GcmTagLength = 128

internal object EncryptedCredentialsSerializer : Serializer<SecureCredentialsProto> {
    override val defaultValue: SecureCredentialsProto = SecureCredentialsProto.getDefaultInstance()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getEntry(KeyAlias, null)
        if (existingKey is KeyStore.SecretKeyEntry) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    override suspend fun readFrom(input: InputStream): SecureCredentialsProto {
        val encryptedData = input.readBytes()
        if (encryptedData.isEmpty()) {
            return defaultValue
        }

        try {
            val iv = encryptedData.copyOfRange(0, GcmIvLength)
            val cipherText = encryptedData.copyOfRange(GcmIvLength, encryptedData.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GcmTagLength, iv))
            val decrypted = cipher.doFinal(cipherText)

            return SecureCredentialsProto.parseFrom(decrypted)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        } catch (exception: GeneralSecurityException) {
            throw CorruptionException("Unexpected error reading proto.", exception)
        }
    }

    override suspend fun writeTo(t: SecureCredentialsProto, output: OutputStream) {
        val plainBytes = t.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainBytes)
        output.write(iv)
        output.write(encryptedBytes)
    }
}
