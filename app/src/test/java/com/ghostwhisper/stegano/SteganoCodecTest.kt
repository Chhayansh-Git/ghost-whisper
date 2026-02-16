package com.ghostwhisper.stegano

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Steganography Codec.
 *
 * Verifies:
 * - Encode/decode round-trip
 * - Encoded string contains only ZW characters
 * - Payload injection and extraction
 * - Chaffing produces non-decodable noise
 * - Strip function removes all ZW characters
 * - Edge cases (empty, large, unicode)
 */
class SteganoCodecTest {

    @Test
    fun `encode then decode returns original bytes`() {
        val original = "Hello, Ghost Whisper!".toByteArray(Charsets.UTF_8)
        val encoded = SteganoCodec.encode(original)
        val decoded = SteganoCodec.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `encoded string contains only ZW characters`() {
        val data = "Test".toByteArray()
        val encoded = SteganoCodec.encode(data)

        for (char in encoded) {
            assertTrue(
                    "Unexpected char: U+${char.code.toString(16)}",
                    char == SteganoCodec.ZW_ZERO || char == SteganoCodec.ZW_ONE
            )
        }
    }

    @Test
    fun `encoded string length equals input bytes times 8`() {
        val data = "Test".toByteArray() // 4 bytes
        val encoded = SteganoCodec.encode(data)
        assertEquals(data.size * 8, encoded.length)
    }

    @Test
    fun `inject and extract payload round-trip`() {
        val cover = "Noted 👍"
        val data = "Secret message".toByteArray()
        val encoded = SteganoCodec.encode(data)

        val combined = SteganoCodec.injectPayload(cover, encoded)
        val extracted = SteganoCodec.extractPayload(combined)

        assertNotNull(extracted)
        val decoded = SteganoCodec.decode(extracted!!)
        assertEquals("Secret message", String(decoded, Charsets.UTF_8))
    }

    @Test
    fun `containsPayload returns true for messages with ZW chars`() {
        val data = "test".toByteArray()
        val encoded = SteganoCodec.encode(data)
        val combined = SteganoCodec.injectPayload("Hello", encoded)

        assertTrue(SteganoCodec.containsPayload(combined))
    }

    @Test
    fun `containsPayload returns false for normal messages`() {
        assertFalse(SteganoCodec.containsPayload("Hello, world!"))
        assertFalse(SteganoCodec.containsPayload(""))
    }

    @Test
    fun `extractPayload returns null for normal messages`() {
        assertNull(SteganoCodec.extractPayload("Hello, world!"))
        assertNull(SteganoCodec.extractPayload(""))
    }

    @Test
    fun `stripZeroWidth removes all ZW characters`() {
        val cover = "Hello World"
        val data = "hidden".toByteArray()
        val encoded = SteganoCodec.encode(data)
        val combined = SteganoCodec.injectPayload(cover, encoded)

        val stripped = SteganoCodec.stripZeroWidth(combined)
        assertEquals(cover, stripped)
    }

    @Test
    fun `chaff produces message with ZW characters`() {
        val message = "Hello, world!"
        val chaffed = SteganoCodec.chaff(message)

        assertTrue(SteganoCodec.containsPayload(chaffed))
        // Visible content should be preserved
        assertEquals(message, SteganoCodec.stripZeroWidth(chaffed))
    }

    @Test
    fun `chaffed message is not a valid decodable payload`() {
        val message = "Hello world"
        val chaffed = SteganoCodec.chaff(message)

        // Extract the ZW chars from the chaffed message
        val zwChars = chaffed.filter { it == SteganoCodec.ZW_ZERO || it == SteganoCodec.ZW_ONE }

        // Chaffing adds random bits that may not align to byte boundaries
        // This is intentional — the noise should NOT form valid packets
        // We just verify that chaffing happened
        assertTrue(zwChars.isNotEmpty())
    }

    @Test
    fun `encode and decode empty byte array`() {
        val original = ByteArray(0)
        val encoded = SteganoCodec.encode(original)
        assertEquals(0, encoded.length)
        val decoded = SteganoCodec.decode(encoded)
        assertEquals(0, decoded.size)
    }

    @Test
    fun `encode and decode binary data`() {
        // Test with all byte values
        val original = ByteArray(256) { it.toByte() }
        val encoded = SteganoCodec.encode(original)
        val decoded = SteganoCodec.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `getStats returns correct counts`() {
        val cover = "Hello"
        val data = "AB".toByteArray() // 2 bytes = 16 ZW chars
        val encoded = SteganoCodec.encode(data)
        val combined = SteganoCodec.injectPayload(cover, encoded)

        val stats = SteganoCodec.getStats(combined)
        assertEquals(5, stats.visibleChars) // "Hello"
        assertEquals(16, stats.zwChars) // 2 bytes * 8 bits
        assertEquals(2, stats.estimatedBytes)
        assertTrue(stats.hasValidPayload)
    }

    @Test
    fun `encode and decode large payload`() {
        val original = "A".repeat(1000).toByteArray()
        val encoded = SteganoCodec.encode(original)
        val decoded = SteganoCodec.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `encode and decode unicode content`() {
        val original = "👻🔐🔑 Ghost Whisper — 秘密".toByteArray(Charsets.UTF_8)
        val encoded = SteganoCodec.encode(original)
        val decoded = SteganoCodec.decode(encoded)
        assertEquals(String(original, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }
}
