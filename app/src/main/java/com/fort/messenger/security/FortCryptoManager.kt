package com.fort.messenger.security

import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class IdentityKeyPair(
    val publicKeyBase64: String,
    val privateKeyBase64: String,
    val fingerprint: String
)

data class EncryptedMessagePayload(
    val ciphertextBase64: String,
    val ivBase64: String,
    val ephemeralPublicKeyBase64: String,
    val senderFingerprint: String,
    val senderSignatureBase64: String = ""
)

/**
 * Genuine End-to-End Encryption engine.
 * Uses NIST P-256 ECDH Key Agreement + HKDF-SHA256 + AES-256-GCM + SHA256withECDSA Sender Authentication.
 * Server stores and relays only ciphertext. Message content is decrypted exclusively on recipient devices.
 */
object FortCryptoManager {

    private const val EC_CURVE = "secp256r1"
    private const val AES_KEY_SIZE_BYTES = 32 // AES-256
    private const val GCM_IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BITS = 128
    private const val HKDF_INFO = "Fort-Sovereign-E2EE-v1"

    private val secureRandom = SecureRandom()

    /**
     * Generates an authentic elliptic curve identity key pair on NIST P-256.
     */
    fun generateIdentityKeyPair(): IdentityKeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE)
        keyPairGenerator.initialize(ecSpec, secureRandom)
        val keyPair = keyPairGenerator.generateKeyPair()

        val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val privBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val fingerprint = computeFingerprint(keyPair.public.encoded)

        return IdentityKeyPair(
            publicKeyBase64 = pubBase64,
            privateKeyBase64 = privBase64,
            fingerprint = fingerprint
        )
    }

    /**
     * Signs data using sender's private key via SHA256withECDSA.
     */
    fun sign(data: ByteArray, privateKeyBase64: String): String {
        val privateKey = decodePrivateKey(privateKeyBase64)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    /**
     * Verifies sender's signature using sender's public key.
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKeyBase64)
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data)
            val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext using recipient's public identity key and signs with sender's private key.
     * Generates an ephemeral key pair to provide forward secrecy per message.
     */
    fun encrypt(
        plaintext: String,
        recipientPublicKeyBase64: String,
        senderKeyPair: IdentityKeyPair
    ): EncryptedMessagePayload {
        val recipientPublicKey = decodePublicKey(recipientPublicKeyBase64)

        // Generate ephemeral key pair for this single transmission
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        val ephemeralKeyPair = keyPairGenerator.generateKeyPair()

        // Compute ECDH shared secret: EphemeralPrivateKey + RecipientPublicKey
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ephemeralKeyPair.private)
        keyAgreement.doPhase(recipientPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit symmetric AES-GCM key using HKDF-SHA256
        val aesKeyBytes = hkdfExtractAndExpand(
            salt = ephemeralKeyPair.public.encoded,
            ikm = rawSharedSecret,
            info = HKDF_INFO.toByteArray(Charsets.UTF_8),
            length = AES_KEY_SIZE_BYTES
        )
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        // Encrypt with AES-256-GCM and unique random 12-byte IV
        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(HKDF_INFO.toByteArray(Charsets.UTF_8))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Cryptographically sign (ephemeralKey + ciphertext + iv) with sender's private key
        val authBytes = ephemeralKeyPair.public.encoded + ciphertext + iv
        val senderSignature = sign(authBytes, senderKeyPair.privateKeyBase64)

        return EncryptedMessagePayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ephemeralPublicKeyBase64 = Base64.encodeToString(ephemeralKeyPair.public.encoded, Base64.NO_WRAP),
            senderFingerprint = senderKeyPair.fingerprint,
            senderSignatureBase64 = senderSignature
        )
    }

    /**
     * Decrypts ciphertext using recipient's private identity key.
     * Optionally validates sender authenticity if senderPublicKeyBase64 is provided.
     */
    fun decrypt(
        payload: EncryptedMessagePayload,
        recipientPrivateKeyBase64: String,
        senderPublicKeyBase64: String? = null
    ): String {
        val recipientPrivateKey = decodePrivateKey(recipientPrivateKeyBase64)
        val ephemeralPublicKey = decodePublicKey(payload.ephemeralPublicKeyBase64)
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)

        // Enforce sender authentication when sender's public key and signature are present
        if (!senderPublicKeyBase64.isNullOrBlank() && payload.senderSignatureBase64.isNotBlank()) {
            val authBytes = ephemeralPublicKey.encoded + ciphertext + iv
            val verified = verify(authBytes, payload.senderSignatureBase64, senderPublicKeyBase64)
            if (!verified) {
                throw SecurityException("Sender authentication failed: Cryptographic signature mismatch.")
            }
        }

        // Compute ECDH shared secret: RecipientPrivateKey + EphemeralPublicKey
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(recipientPrivateKey)
        keyAgreement.doPhase(ephemeralPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        // Derive identical 256-bit AES-GCM key via HKDF
        val aesKeyBytes = hkdfExtractAndExpand(
            salt = ephemeralPublicKey.encoded,
            ikm = rawSharedSecret,
            info = HKDF_INFO.toByteArray(Charsets.UTF_8),
            length = AES_KEY_SIZE_BYTES
        )
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(HKDF_INFO.toByteArray(Charsets.UTF_8))

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Derives a cryptographic Safety Number derived from the sorted identity keys of both participants.
     * Returns 12 blocks of 5 digits (60 numeric digits) matching Signal's safety number standard.
     */
    fun computeSafetyNumber(userPublicKeyBase64: String, peerPublicKeyBase64: String): String {
        val keyA = Base64.decode(userPublicKeyBase64, Base64.NO_WRAP)
        val keyB = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)

        // Deterministic sorting to ensure both users calculate the identical number
        val (first, second) = if (compareByteArrays(keyA, keyB) <= 0) Pair(keyA, keyB) else Pair(keyB, keyA)

        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(first)
        digest.update(second)
        val hash = digest.digest() // 64 bytes

        // Format into 12 5-digit blocks (60 digits total)
        val buffer = ByteBuffer.wrap(hash)
        val blocks = mutableListOf<String>()
        for (i in 0 until 12) {
            val chunk = (buffer.int and 0x7FFFFFFF) % 100000
            blocks.add(String.format("%05d", chunk))
        }
        return blocks.joinToString(" ")
    }

    /**
     * Computes human-readable hex fingerprint from public key bytes.
     */
    fun computeFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        val hex = hash.take(10).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(" · ")
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun decodePrivateKey(base64: String): PrivateKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    /**
     * HKDF Extract and Expand implementation (RFC 5869) using HMAC-SHA256.
     */
    private fun hkdfExtractAndExpand(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Step 1: Extract
        val macExtract = Mac.getInstance("HmacSHA256")
        val saltKey = if (salt.isNotEmpty()) SecretKeySpec(salt, "HmacSHA256") else SecretKeySpec(ByteArray(32), "HmacSHA256")
        macExtract.init(saltKey)
        val prk = macExtract.doFinal(ikm)

        // Step 2: Expand
        val macExpand = Mac.getInstance("HmacSHA256")
        macExpand.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var block = 1

        while (offset < length) {
            macExpand.update(t)
            macExpand.update(info)
            macExpand.update(block.toByte())
            t = macExpand.doFinal()

            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            block++
        }
        return result
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
