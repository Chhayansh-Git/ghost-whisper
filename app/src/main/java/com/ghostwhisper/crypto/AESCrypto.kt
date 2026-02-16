package com.ghostwhisper.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM Encryption Engine for Ghost Whisper.
 *
 * Provides authenticated encryption with associated data (AEAD). Every encryption uses a fresh
 * random IV via SecureRandom (CSPRNG).
 *
 * Security guarantees:
 * - Confidentiality: AES-256 (2^256 keyspace)
 * - Integrity: GCM 128-bit authentication tag
 * - Freshness: Unique 12-byte IV per message
 */
object AESCrypto {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BYTES = 32 // 256 bits
    private const val IV_SIZE_BYTES = 12 // 96 bits (NIST recommended for GCM)
    private const val AUTH_TAG_BITS = 128 // GCM auth tag length
    private const val KEY_ID_LENGTH = 4 // First 4 hex chars of SHA-256

    /** Encrypted payload containing IV and ciphertext (with appended auth tag). */
    data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {
        /** Base64-encode the IV for wire format. */
        fun ivBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(iv)

        /** Base64-encode the ciphertext for wire format. */
        fun ciphertextBase64(): String =
                Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false
            return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int = iv.contentHashCode() * 31 + ciphertext.contentHashCode()
    }

    /**
     * Generate a cryptographically secure 256-bit AES key. Uses Android's SecureRandom backed by
     * the OS CSPRNG.
     *
     * @return 32-byte random key
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Derive a short Key ID from a channel name. Uses first 4 hex characters of
     * SHA-256(channelName).
     *
     * @param channelName Human-readable channel name
     * @return 4-character hex string (e.g., "8f3a")
     */
    fun deriveKeyId(channelName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(channelName.toByteArray(Charsets.UTF_8))
        return hash.take(2).joinToString("") { "%02x".format(it) }
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     *
     * A fresh 12-byte IV is generated for every call via SecureRandom. The GCM auth tag (128 bits)
     * is appended to the ciphertext by the cipher.
     *
     * @param plaintext The message to encrypt
     * @param key The 256-bit AES key (32 bytes)
     * @return EncryptedPayload containing IV and ciphertext+authTag
     * @throws IllegalArgumentException if key is not 32 bytes
     */
    fun encrypt(plaintext: String, key: ByteArray): EncryptedPayload {
        require(key.size == KEY_SIZE_BYTES) {
            "Key must be $KEY_SIZE_BYTES bytes (256 bits), got ${key.size}"
        }

        // Generate fresh IV (CRITICAL: never reuse with the same key)
        val iv = ByteArray(IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(key, ALGORITHM)
        val gcmSpec = GCMParameterSpec(AUTH_TAG_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)

        return EncryptedPayload(iv = iv, ciphertext = ciphertext)
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     *
     * Verifies the GCM authentication tag before returning plaintext. Throws AEADBadTagException if
     * the ciphertext was tampered with or the key is wrong.
     *
     * @param ciphertext The encrypted data (with appended auth tag)
     * @param key The 256-bit AES key (32 bytes)
     * @param iv The initialization vector used during encryption (12 bytes)
     * @return Decrypted plaintext string
     * @throws javax.crypto.AEADBadTagException if authentication fails (wrong key or tampered data)
     * @throws IllegalArgumentException if key or IV sizes are incorrect
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): String {
        require(key.size == KEY_SIZE_BYTES) {
            "Key must be $KEY_SIZE_BYTES bytes (256 bits), got ${key.size}"
        }
        require(iv.size == IV_SIZE_BYTES) {
            "IV must be $IV_SIZE_BYTES bytes (96 bits), got ${iv.size}"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(key, ALGORITHM)
        val gcmSpec = GCMParameterSpec(AUTH_TAG_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    /**
     * Decrypt from Base64-encoded components. Convenience method for decoding wire-format packets.
     */
    fun decryptBase64(ciphertextB64: String, keyB64: String, ivB64: String): String {
        val decoder = Base64.getUrlDecoder()
        val ciphertext = decoder.decode(ciphertextB64)
        val key = decoder.decode(keyB64)
        val iv = decoder.decode(ivB64)
        return decrypt(ciphertext, key, iv)
    }

    /** Encode a key to Base64 for storage. */
    fun keyToBase64(key: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key)
    }

    /** Decode a key from Base64 storage. */
    fun keyFromBase64(base64Key: String): ByteArray {
        return Base64.getUrlDecoder().decode(base64Key)
    }
}
