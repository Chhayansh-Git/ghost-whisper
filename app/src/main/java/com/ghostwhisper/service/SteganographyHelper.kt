package com.ghostwhisper.service

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

object SteganographyHelper {

    private const val HEADER_SIGNATURE = "GW" // Ghost Whisper signature (2 bytes)

    /**
     * Encodes a secret message into a secret message into a GIF Byte Stream (Animated GIF support).
     * Uses Application Extension Block structure injection.
     */
    fun encode(gifBytes: ByteArray, message: String): ByteArray {
        return GifHelper.embed(gifBytes, message)
    }

    /** Decodes a secret message from a GIF Byte Stream. */
    fun decode(gifBytes: ByteArray): String? {
        return GifHelper.extract(gifBytes)
    }

    /**
     * Encodes a secret message into a Bitmap using Least Significant Bit (LSB) steganography.
     * Format: [Signature (2 bytes)][Length (4 bytes)][Message Payload]
     */
    fun encode(bitmap: Bitmap, message: String): Bitmap? {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = mutableBitmap.width
        val height = mutableBitmap.height

        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val length = messageBytes.size

        // Prepare bits: Signature + Length + Message
        val signatureBits =
                toBitArray(HEADER_SIGNATURE.toByteArray(StandardCharsets.UTF_8)) // 16 bits
        val lengthBits = intToBitArray(length) // 32 bits
        val messageBits = toBitArray(messageBytes) // n * 8 bits

        val totalBits = signatureBits + lengthBits + messageBits

        if (totalBits.size > width * height * 3) {
            return null // Message too long for this image
        }

        var bitIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitIndex >= totalBits.size) break

                val pixel = mutableBitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                val a = Color.alpha(pixel)

                // Encode into Red channel LSB
                if (bitIndex < totalBits.size) {
                    r = (r and 0xFE) or totalBits[bitIndex]
                    bitIndex++
                }

                // Encode into Green channel LSB
                if (bitIndex < totalBits.size) {
                    g = (g and 0xFE) or totalBits[bitIndex]
                    bitIndex++
                }

                // Encode into Blue channel LSB
                if (bitIndex < totalBits.size) {
                    b = (b and 0xFE) or totalBits[bitIndex]
                    bitIndex++
                }

                mutableBitmap.setPixel(x, y, Color.argb(a, r, g, b))
            }
            if (bitIndex >= totalBits.size) break
        }

        return mutableBitmap
    }

    /** Decodes a secret message from a Bitmap. */
    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height

        val totalPixels = width * height
        // We need at least 2 bytes (Sig) + 4 bytes (Len) = 6 bytes = 48 bits
        if (totalPixels * 3 < 48) return null

        val extractedHeaderBits = ArrayList<Int>()
        val extractedMessageBits = ArrayList<Int>()

        var signatureVerified = false
        var messageLength = -1 // in bytes

        var currentBitIndex = 0 // Total bits read from the image

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Extract 3 bits per pixel
                val pixelBits = listOf(r and 1, g and 1, b and 1)

                for (bit in pixelBits) {
                    if (!signatureVerified) {
                        extractedHeaderBits.add(bit)
                        currentBitIndex++

                        // Check header once we have enough bits (48 bits = 6 bytes)
                        if (extractedHeaderBits.size >= 48) {
                            val headerBytes = toByteArray(extractedHeaderBits)
                            val sigBytes = headerBytes.copyOfRange(0, 2)
                            val lenBytes = headerBytes.copyOfRange(2, 6)

                            val sig = String(sigBytes, StandardCharsets.UTF_8)
                            if (sig != HEADER_SIGNATURE) return null // Invalid signature

                            signatureVerified = true
                            messageLength = bytesToInt(lenBytes)

                            // If messageLength is 0, return empty string
                            if (messageLength == 0) return ""
                        }
                    } else {
                        // If signature is verified, start collecting message bits
                        if (extractedMessageBits.size < messageLength * 8) {
                            extractedMessageBits.add(bit)
                            currentBitIndex++
                        } else {
                            // Message is fully extracted, break out of loops
                            break
                        }
                    }
                }
                if (signatureVerified && extractedMessageBits.size >= messageLength * 8) break
            }
            if (signatureVerified && extractedMessageBits.size >= messageLength * 8) break
        }

        // If we reached here and messageLength was determined, but not enough message bits were
        // found
        if (signatureVerified && extractedMessageBits.size == messageLength * 8) {
            return String(toByteArray(extractedMessageBits), StandardCharsets.UTF_8)
        }

        return null
    }

    private fun intToBitArray(value: Int): IntArray {
        val bits = IntArray(32)
        for (i in 0 until 32) {
            bits[i] = (value shr (31 - i)) and 1
        }
        return bits
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        var result = 0
        for (byte in bytes) {
            result = (result shl 8) or (byte.toInt() and 0xFF)
        }
        return result
    }

    private fun toBitArray(bytes: ByteArray): IntArray {
        val bits = IntArray(bytes.size * 8)
        for (i in bytes.indices) {
            val byteVal = bytes[i].toInt()
            for (j in 0 until 8) {
                bits[i * 8 + j] = (byteVal shr (7 - j)) and 1
            }
        }
        return bits
    }

    private fun toByteArray(bits: List<Int>): ByteArray {
        val byteCount = bits.size / 8
        val bytes = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var byteVal = 0
            for (j in 0 until 8) {
                byteVal = (byteVal shl 1) or bits[i * 8 + j]
            }
            bytes[i] = byteVal.toByte()
        }
        return bytes
    }
}
