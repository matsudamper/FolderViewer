package net.matsudamper.folderviewer.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CryptoHelper {
    private val provider = "AndroidKeyStore"
    private val alias = "app_credentials_key"
    private val cipherTransformation = "AES/GCM/NoPadding"

    private val keyStore = KeyStore.getInstance(provider).apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return combined
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherTransformation)
        // GCM IV is 12 bytes
        val ivSize = 12
        require(data.size >= ivSize) { "Invalid data" }

        val iv = ByteArray(ivSize)
        System.arraycopy(data, 0, iv, 0, ivSize)
        val encrypted = ByteArray(data.size - ivSize)
        System.arraycopy(data, ivSize, encrypted, 0, encrypted.size)

        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(encrypted)
    }
}
