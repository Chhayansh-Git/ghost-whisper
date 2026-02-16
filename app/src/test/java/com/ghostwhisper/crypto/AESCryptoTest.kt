package com.ghostwhisper.crypto

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AES-256-GCM encryption engine.
 *
 * Verifies:
 * - Encrypt/decrypt round-trip
 * - Key generation produces correct size
 * - Key ID derivation is deterministic
 * - Wrong key fails decryption
 * - IV uniqueness across encryptions
 * - Empty and unicode string handling
 * - Tampered ciphertext detection
 */
class AESCryptoTest {

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val key = AESCrypto.generateKey()
        val plaintext = "Hello, Ghost Whisper!"

        val encrypted = AESCrypto.encrypt(plaintext, key)
        val decrypted = AESCrypto.decrypt(encrypted.ciphertext, key, encrypted.iv)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `generateKey produces 32-byte key`() {
        val key = AESCrypto.generateKey()
        assertEquals(32, key.size)
    }

    @Test
    fun `generateKey produces unique keys`() {
        val key1 = AESCrypto.generateKey()
        val key2 = AESCrypto.generateKey()
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `deriveKeyId is deterministic`() {
        val id1 = AESCrypto.deriveKeyId("Study Group")
        val id2 = AESCrypto.deriveKeyId("Study Group")
        assertEquals(id1, id2)
        assertEquals(4, id1.length)
    }

    @Test
    fun `deriveKeyId produces different IDs for different names`() {
        val id1 = AESCrypto.deriveKeyId("Study Group")
        val id2 = AESCrypto.deriveKeyId("Project Team")
        assertNotEquals(id1, id2)
    }

    @Test(expected = Exception::class)
    fun `decrypt with wrong key throws exception`() {
        val key1 = AESCrypto.generateKey()
        val key2 = AESCrypto.generateKey()
        val plaintext = "Secret message"

        val encrypted = AESCrypto.encrypt(plaintext, key1)
        // This should throw AEADBadTagException
        AESCrypto.decrypt(encrypted.ciphertext, key2, encrypted.iv)
    }

    @Test
    fun `IV is unique per encryption`() {
        val key = AESCrypto.generateKey()
        val ivs = mutableSetOf<String>()

        repeat(100) {
            val encrypted = AESCrypto.encrypt("test", key)
            val ivHex = encrypted.iv.joinToString("") { "%02x".format(it) }
            ivs.add(ivHex)
        }

        // All 100 IVs should be unique
        assertEquals(100, ivs.size)
    }

    @Test
    fun `encrypt and decrypt empty string`() {
        val key = AESCrypto.generateKey()
        val encrypted = AESCrypto.encrypt("", key)
        val decrypted = AESCrypto.decrypt(encrypted.ciphertext, key, encrypted.iv)
        assertEquals("", decrypted)
    }

    @Test
    fun `encrypt and decrypt unicode characters`() {
        val key = AESCrypto.generateKey()
        val plaintext = "こんにちは 🌍 Привет مرحبا"

        val encrypted = AESCrypto.encrypt(plaintext, key)
        val decrypted = AESCrypto.decrypt(encrypted.ciphertext, key, encrypted.iv)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt long message`() {
        val key = AESCrypto.generateKey()
        val plaintext = "A".repeat(10000)

        val encrypted = AESCrypto.encrypt(plaintext, key)
        val decrypted = AESCrypto.decrypt(encrypted.ciphertext, key, encrypted.iv)

        assertEquals(plaintext, decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypt with wrong key size throws`() {
        val shortKey = ByteArray(16) // 128-bit, not 256-bit
        AESCrypto.encrypt("test", shortKey)
    }

    @Test(expected = Exception::class)
    fun `tampered ciphertext fails authentication`() {
        val key = AESCrypto.generateKey()
        val encrypted = AESCrypto.encrypt("Secret", key)

        // Tamper with the ciphertext
        val tampered = encrypted.ciphertext.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        // Should throw AEADBadTagException
        AESCrypto.decrypt(tampered, key, encrypted.iv)
    }

    @Test
    fun `base64 key round-trip`() {
        val key = AESCrypto.generateKey()
        val base64 = AESCrypto.keyToBase64(key)
        val decoded = AESCrypto.keyFromBase64(base64)
        assertArrayEquals(key, decoded)
    }
}
