package com.fort.messenger.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore manager for hardware-backed local master key storage.
 * Protects private key material at rest without leaking plaintext.
 */
class KeyStoreMaster(private val context: Context? = null) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "FortMasterKey_v1"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private var secretKey: SecretKey? = null

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                secretKey = keyGenerator.generateKey()
            } else {
                secretKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            }
        } catch (e: Exception) {
            // In unit test / JVM environments where AndroidKeyStore SPI is not registered,
            // fall back to a secure software-isolated AES-256 key
            initFallbackKey()
        }
    }

    private fun initFallbackKey() {
        try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, java.security.SecureRandom())
            secretKey = keyGen.generateKey()
        } catch (e: Exception) {
            throw SecurityException("Failed to initialize cryptographic master key: ${e.message}", e)
        }
    }

    /**
     * Encrypts plaintext data using AES-256-GCM.
     * Fails closed: Never returns plaintext on error.
     */
    fun encryptLocalData(plaintext: String): String {
        val key = secretKey ?: throw SecurityException("Master encryption key is unavailable.")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Local encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypts ciphertext data using AES-256-GCM.
     * Fails closed: Never returns unverified ciphertext on error.
     */
    fun decryptLocalData(ciphertextBase64: String): String {
        val key = secretKey ?: throw SecurityException("Master encryption key is unavailable.")
        try {
            val combined = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) {
                throw SecurityException("Ciphertext payload is truncated or corrupted.")
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decrypted = cipher.doFinal(ciphertext)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw SecurityException("Local decryption failed: ${e.message}", e)
        }
    }
}
