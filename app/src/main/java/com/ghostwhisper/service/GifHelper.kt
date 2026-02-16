package com.ghostwhisper.service

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Helper to manipulate GIF byte streams for steganography.
 *
 * Strategy: Structure Injection (Application Extension) We inject a custom Application Extension
 * Block containing the encrypted message. This preserves the original animation frames
 * bit-perfectly.
 *
 * Block Structure: 0x21 (Extension Introducer) 0xFF (Application Extension Label) 0x0B (Block Size:
 * 11 bytes) "GHOSTWSP" (8 bytes Application Identifier) "1.0" (3 bytes Authentication Code) [Data
 * Sub-blocks] 0x00 (Block Terminator)
 */
object GifHelper {
    private const val APP_ID = "GHOSTWSP"
    private const val AUTH_CODE = "1.0"

    // Signatures
    private val GIF87a = "GIF87a".toByteArray(StandardCharsets.US_ASCII)
    private val GIF89a = "GIF89a".toByteArray(StandardCharsets.US_ASCII)

    fun isGif(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        val header = bytes.sliceArray(0 until 6)
        return header.contentEquals(GIF87a) || header.contentEquals(GIF89a)
    }

    /**
     * Embeds a message into the GIF by inserting a custom Application Extension Block. The block is
     * inserted immediately after the Header and Global Color Table (if present).
     */
    fun embed(gifBytes: ByteArray, message: String): ByteArray {
        if (!isGif(gifBytes)) throw IllegalArgumentException("Not a valid GIF file")

        val output = ByteArrayOutputStream()
        var index = 0

        // 1. Write Header (6 bytes)
        output.write(gifBytes, 0, 6)
        index += 6

        // 2. Parse Logical Screen Descriptor (7 bytes)
        if (index + 7 > gifBytes.size) throw IllegalArgumentException("Corrupt GIF (Truncated LSD)")
        val lsd = gifBytes.sliceArray(index until index + 7)
        output.write(lsd)
        index += 7

        // Check for Global Color Table (GCT) flag
        // Packet Fields is at offset 4 in LSD (so index - 3 from current pos, or lsd[4])
        val packedFields = lsd[4].toInt() and 0xFF
        val hasGCT = (packedFields and 0x80) != 0

        if (hasGCT) {
            val gctSizeExp = packedFields and 0x07
            val gctSize = 3 * (1 shl (gctSizeExp + 1)) // 3 * 2^(N+1)

            if (index + gctSize > gifBytes.size)
                    throw IllegalArgumentException("Corrupt GIF (Truncated GCT)")
            output.write(gifBytes, index, gctSize)
            index += gctSize
        }

        // 3. Inject our Application Extension Block here
        writeApplicationExtension(output, message)

        // 4. Write the rest of the file
        output.write(gifBytes, index, gifBytes.size - index)

        return output.toByteArray()
    }

    /** Extracts the message from the GIF's custom Application Extension Block. */
    fun extract(gifBytes: ByteArray): String? {
        if (!isGif(gifBytes)) return null

        var index = 0
        // Skip Header
        index += 6

        // Skip LSD
        if (index + 7 > gifBytes.size) return null
        val packedFields = gifBytes[index + 4].toInt() and 0xFF
        index += 7

        // Skip GCT if present
        if ((packedFields and 0x80) != 0) {
            val gctSizeExp = packedFields and 0x07
            val gctSize = 3 * (1 shl (gctSizeExp + 1))
            index += gctSize
        }

        // Now parse blocks until we find ours or hit EOF
        while (index < gifBytes.size) {
            val byte = gifBytes[index].toInt() and 0xFF

            // Image Descriptor or Trailer (0x3B) or Extension (0x21)
            if (byte == 0x3B) break // Trailer

            if (byte == 0x21) {
                // Extension
                if (index + 2 > gifBytes.size) break
                val label = gifBytes[index + 1].toInt() and 0xFF

                if (label == 0xFF) {
                    // Application Extension
                    // Check if it's OURS
                    if (isGhostWhisperBlock(gifBytes, index)) {
                        return readApplicationExtensionData(gifBytes, index)
                    }
                }

                // Skip this extension
                index += 2 // skip introducer + label
                // Skip blocks
                while (index < gifBytes.size) {
                    val blockSize = gifBytes[index].toInt() and 0xFF
                    index++
                    if (blockSize == 0) break // Block terminator
                    index += blockSize
                }
            } else if (byte == 0x2C) {
                // Image Descriptor (0x2C)
                index += 10 // Image descriptor size (without LCT)
                // Check local color table
                if (index - 1 < gifBytes.size) {
                    val localPacked = gifBytes[index - 1].toInt() and 0xFF
                    if ((localPacked and 0x80) != 0) {
                        val lctSizeExp = localPacked and 0x07
                        val lctSize = 3 * (1 shl (lctSizeExp + 1))
                        index += lctSize
                    }
                }
                // Skip LZW data
                if (index < gifBytes.size) {
                    index++ // LZW Minimum Code Size
                    while (index < gifBytes.size) {
                        val blockSize = gifBytes[index].toInt() and 0xFF
                        index++
                        if (blockSize == 0) break
                        index += blockSize
                    }
                }
            } else {
                // Unknown byte or sync error, simpler parser might just linear scan for signature
                // But strictly, we should parse.
                // Fallback: Just skip 1 byte if we are lost? No, precise parsing is key.
                // If we are here, it might be a text extension or comment, handled by general 0x21
                // logic ideally.
                // But header is 0x21.
                // If not 0x21 and not 0x2C and not 0x3B... corrupt or weird block?
                index++
            }
        }

        return null
    }

    private fun writeApplicationExtension(output: ByteArrayOutputStream, message: String) {
        val payload = message.toByteArray(StandardCharsets.UTF_8)

        // Header
        output.write(0x21) // Extension Introducer
        output.write(0xFF) // App Extension Label
        output.write(0x0B) // Block Size (11)
        output.write(APP_ID.toByteArray(StandardCharsets.US_ASCII)) // 8 bytes
        output.write(AUTH_CODE.toByteArray(StandardCharsets.US_ASCII)) // 3 bytes

        // Data Sub-blocks
        var offset = 0
        while (offset < payload.size) {
            val length = minOf(255, payload.size - offset)
            output.write(length)
            output.write(payload, offset, length)
            offset += length
        }

        // Terminator
        output.write(0x00)
    }

    private fun isGhostWhisperBlock(bytes: ByteArray, startIndex: Int): Boolean {
        // Expect: 21 FF 0B G H O S T W S P 1 . 0
        if (startIndex + 14 > bytes.size) return false

        // Check "GHOSTWSP" at startIndex + 3
        val idBytes = APP_ID.toByteArray(StandardCharsets.US_ASCII)
        for (i in idBytes.indices) {
            if (bytes[startIndex + 3 + i] != idBytes[i]) return false
        }

        // Check "1.0" at startIndex + 11
        val authBytes = AUTH_CODE.toByteArray(StandardCharsets.US_ASCII)
        for (i in authBytes.indices) {
            if (bytes[startIndex + 11 + i] != authBytes[i]) return false
        }

        return true
    }

    private fun readApplicationExtensionData(bytes: ByteArray, startIndex: Int): String {
        // Skip header (2 bytes) + block size byte + 11 bytes ID/Auth
        var index = startIndex + 14

        val output = ByteArrayOutputStream()

        while (index < bytes.size) {
            val blockSize = bytes[index].toInt() and 0xFF
            index++

            if (blockSize == 0) break // Terminator

            if (index + blockSize > bytes.size) break
            output.write(bytes, index, blockSize)
            index += blockSize
        }

        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }
}
