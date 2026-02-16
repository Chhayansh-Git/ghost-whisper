package com.ghostwhisper.data

import com.ghostwhisper.crypto.AESCrypto
import com.ghostwhisper.data.model.GhostPacket
import com.ghostwhisper.stegano.SteganoCodec
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the full Ghost Whisper pipeline.
 *
 * Tests the complete flow: Plaintext → Encrypt → Packet → ZW Encode → ZW Decode → Packet → Decrypt
 * → Plaintext
 */
class GhostPacketTest {

    @Test
    fun `packet JSON serialization round-trip`() {
        val packet =
                GhostPacket(
                        version = 1,
                        keyId = "8f3a",
                        iv = "dGVzdF9pdg==",
                        ciphertext = "dGVzdF9jaXBoZXJ0ZXh0"
                )

        val json = packet.toJson()
        val restored = GhostPacket.fromJson(json)

        assertEquals(packet.version, restored.version)
        assertEquals(packet.keyId, restored.keyId)
        assertEquals(packet.iv, restored.iv)
        assertEquals(packet.ciphertext, restored.ciphertext)
    }

    @Test
    fun `packet bytes serialization round-trip`() {
        val packet = GhostPacket(keyId = "ab12", iv = "dGVzdA==", ciphertext = "Y2lwaGVy")

        val bytes = packet.toBytes()
        val restored = GhostPacket.fromBytes(bytes)

        assertEquals(packet.keyId, restored.keyId)
        assertEquals(packet.iv, restored.iv)
        assertEquals(packet.ciphertext, restored.ciphertext)
    }

    @Test
    fun `full pipeline - plaintext to ZW and back`() {
        val originalMessage = "Let's bunk class and go to the canteen"
        val coverMessage = "Noted 👍"
        val channelName = "Study Group"

        // ─── SENDER SIDE ─────────────────────────────────────
        // Step 1: Generate key
        val key = AESCrypto.generateKey()
        val keyId = AESCrypto.deriveKeyId(channelName)

        // Step 2: Encrypt
        val encrypted = AESCrypto.encrypt(originalMessage, key)

        // Step 3: Build packet
        val packet =
                GhostPacket(
                        keyId = keyId,
                        iv = encrypted.ivBase64(),
                        ciphertext = encrypted.ciphertextBase64()
                )

        // Step 4: Encode to ZW
        val zwPayload = SteganoCodec.encode(packet.toBytes())

        // Step 5: Inject into cover
        val fullMessage = SteganoCodec.injectPayload(coverMessage, zwPayload)

        // Verify the visible text looks normal
        assertEquals(coverMessage, SteganoCodec.stripZeroWidth(fullMessage))

        // ─── RECEIVER SIDE ───────────────────────────────────
        // Step 6: Detect ZW payload
        assertTrue(SteganoCodec.containsPayload(fullMessage))

        // Step 7: Extract payload
        val extractedPayload = SteganoCodec.extractPayload(fullMessage)
        assertNotNull(extractedPayload)

        // Step 8: Decode ZW → bytes → JSON
        val decodedBytes = SteganoCodec.decode(extractedPayload!!)
        val decodedPacket = GhostPacket.fromBytes(decodedBytes)

        // Step 9: Verify key ID
        assertEquals(keyId, decodedPacket.keyId)

        // Step 10: Decrypt
        val decryptedMessage =
                AESCrypto.decrypt(
                        ciphertext = decodedPacket.ciphertextBytes(),
                        key = key,
                        iv = decodedPacket.ivBytes()
                )

        // Step 11: Verify
        assertEquals(originalMessage, decryptedMessage)
    }

    @Test
    fun `silent fail - wrong key produces no output`() {
        val message = "Secret"
        val senderKey = AESCrypto.generateKey()
        val receiverKey = AESCrypto.generateKey() // Different key!

        // Sender encrypts
        val encrypted = AESCrypto.encrypt(message, senderKey)
        val packet =
                GhostPacket(
                        keyId = AESCrypto.deriveKeyId("Channel"),
                        iv = encrypted.ivBase64(),
                        ciphertext = encrypted.ciphertextBase64()
                )

        // Receiver tries to decrypt with wrong key
        var decryptionSucceeded = false
        try {
            AESCrypto.decrypt(
                    ciphertext = packet.ciphertextBytes(),
                    key = receiverKey,
                    iv = packet.ivBytes()
            )
            decryptionSucceeded = true
        } catch (e: Exception) {
            // Expected: should fail silently
        }

        assertFalse("Decryption should fail with wrong key", decryptionSucceeded)
    }

    @Test
    fun `pipeline with unicode messages`() {
        val message = "こんにちは 👻 Let's test unicode: مرحبا"
        val key = AESCrypto.generateKey()

        val encrypted = AESCrypto.encrypt(message, key)
        val packet =
                GhostPacket(
                        keyId = "test",
                        iv = encrypted.ivBase64(),
                        ciphertext = encrypted.ciphertextBase64()
                )

        val zwPayload = SteganoCodec.encode(packet.toBytes())
        val fullMessage = SteganoCodec.injectPayload("OK", zwPayload)

        // Decode path
        val extracted = SteganoCodec.extractPayload(fullMessage)!!
        val decoded = GhostPacket.fromBytes(SteganoCodec.decode(extracted))
        val decrypted = AESCrypto.decrypt(decoded.ciphertextBytes(), key, decoded.ivBytes())

        assertEquals(message, decrypted)
    }
}
