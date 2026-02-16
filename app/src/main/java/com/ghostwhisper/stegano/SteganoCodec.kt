package com.ghostwhisper.stegano

import java.security.SecureRandom

/**
 * Steganography Codec for Ghost Whisper.
 *
 * Encodes arbitrary byte data into invisible Zero-Width Unicode characters and decodes them back.
 * Uses a binary encoding scheme:
 * - Bit 0 → \u200B (Zero-Width Space)
 * - Bit 1 → \u200C (Zero-Width Non-Joiner)
 *
 * The encoded string is completely invisible when rendered in any standard text display (WhatsApp,
 * browsers, text editors, etc.).
 *
 * Also provides:
 * - Payload injection/extraction into cover messages
 * - Chaffing (random ZW noise injection for traffic analysis defense)
 */
object SteganoCodec {

    /** Zero-Width Space — represents binary 0 */
    const val ZW_ZERO = '\u200B'

    /** Zero-Width Non-Joiner — represents binary 1 */
    const val ZW_ONE = '\u200C'

    /** Delimiter between cover message and hidden payload */
    private const val PAYLOAD_DELIMITER = '\u200D' // Zero-Width Joiner (used as boundary marker)

    /** Both ZW chars used in encoding */
    private val ZW_CHARS = setOf(ZW_ZERO, ZW_ONE, PAYLOAD_DELIMITER)

    /**
     * Encode a byte array into a Zero-Width character string.
     *
     * Each byte is converted to 8 bits, each bit mapped to a ZW character. Result length =
     * input.size * 8 ZW characters.
     *
     * @param data Raw bytes to encode
     * @return String containing only ZW characters (invisible)
     */
    fun encode(data: ByteArray): String {
        val sb = StringBuilder(data.size * 8)
        for (byte in data) {
            for (bit in 7 downTo 0) {
                val isSet = (byte.toInt() shr bit) and 1 == 1
                sb.append(if (isSet) ZW_ONE else ZW_ZERO)
            }
        }
        return sb.toString()
    }

    /**
     * Decode a Zero-Width character string back to bytes.
     *
     * Ignores any non-ZW characters in the input. Input must contain a multiple of 8 ZW characters.
     *
     * @param zwString String containing ZW-encoded data
     * @return Decoded byte array
     * @throws IllegalArgumentException if ZW char count is not a multiple of 8
     */
    fun decode(zwString: String): ByteArray {
        // Extract only encoding chars (ZW_ZERO and ZW_ONE), ignore delimiters and other chars
        val bits = zwString.filter { it == ZW_ZERO || it == ZW_ONE }

        require(bits.length % 8 == 0) {
            "Invalid ZW data: bit count (${bits.length}) must be a multiple of 8"
        }

        val bytes = ByteArray(bits.length / 8)
        for (i in bytes.indices) {
            var value = 0
            for (bit in 0 until 8) {
                val char = bits[i * 8 + bit]
                if (char == ZW_ONE) {
                    value = value or (1 shl (7 - bit))
                }
            }
            bytes[i] = value.toByte()
        }
        return bytes
    }

    /**
     * Inject an encoded payload into a cover message.
     *
     * The payload is appended after the cover message, separated by a ZW Joiner delimiter (also
     * invisible).
     *
     * @param coverMessage The visible, innocent-looking text
     * @param encodedPayload The ZW-encoded hidden data (from [encode])
     * @return Combined string: visible cover + invisible payload
     */
    fun injectPayload(coverMessage: String, encodedPayload: String): String {
        return "$coverMessage$PAYLOAD_DELIMITER$encodedPayload"
    }

    /**
     * Extract the hidden payload from a message.
     *
     * Looks for ZW character sequences in the message. Returns null if no payload is detected.
     *
     * @param message The full message (cover + potential payload)
     * @return The ZW-encoded payload string, or null if none found
     */
    fun extractPayload(message: String): String? {
        // Strategy 1: Look for delimiter-based payload
        val delimiterIndex = message.indexOf(PAYLOAD_DELIMITER)
        if (delimiterIndex != -1) {
            val afterDelimiter = message.substring(delimiterIndex + 1)
            val payload = afterDelimiter.filter { it == ZW_ZERO || it == ZW_ONE }
            if (payload.length >= 8) {
                return payload
            }
        }

        // Strategy 2: Look for any significant cluster of ZW characters
        val zwChars = StringBuilder()
        for (char in message) {
            if (char == ZW_ZERO || char == ZW_ONE) {
                zwChars.append(char)
            }
        }

        // Need at least 8 ZW chars (1 byte) to be a valid payload
        return if (zwChars.length >= 8 && zwChars.length % 8 == 0) {
            zwChars.toString()
        } else {
            null
        }
    }

    /**
     * Detect if a message contains any Zero-Width characters.
     *
     * Fast check without full extraction — useful for pre-filtering.
     *
     * @param message Message to scan
     * @return true if ZW encoding characters are present
     */
    fun containsPayload(message: String): Boolean {
        return message.any { it == ZW_ZERO || it == ZW_ONE }
    }

    /**
     * Inject random ZW noise into a normal message (Chaffing).
     *
     * Inserts random ZW characters between visible characters at random intervals. This makes ALL
     * messages contain ZW characters, defeating traffic analysis that looks for "messages with ZW =
     * secret, messages without = normal."
     *
     * The noise is intentionally NOT a valid encoded payload (random length, doesn't align to byte
     * boundaries).
     *
     * @param message The normal visible message
     * @param intensity How many ZW chars to inject per visible char (1-5, default 2)
     * @return Message with random ZW noise sprinkled throughout
     */
    fun chaff(message: String, intensity: Int = 2): String {
        val random = SecureRandom()
        val sb = StringBuilder(message.length * (1 + intensity))

        for (char in message) {
            sb.append(char)
            // Randomly decide whether to inject noise after this character
            if (random.nextBoolean()) {
                val noiseCount = random.nextInt(intensity) + 1
                repeat(noiseCount) { sb.append(if (random.nextBoolean()) ZW_ZERO else ZW_ONE) }
            }
        }

        // Ensure the noise doesn't accidentally form a valid byte boundary
        // by adding 1-7 extra random ZW chars at the end
        val extraBits = random.nextInt(7) + 1
        repeat(extraBits) { sb.append(if (random.nextBoolean()) ZW_ZERO else ZW_ONE) }

        return sb.toString()
    }

    /**
     * Strip all Zero-Width characters from a message.
     *
     * Used by Clipboard Guard to clean copied messages.
     *
     * @param message Message potentially containing ZW characters
     * @return Clean message with all ZW characters removed
     */
    fun stripZeroWidth(message: String): String {
        return message.filter { it !in ZW_CHARS }
    }

    /**
     * Get statistics about ZW characters in a message. Useful for debugging and the TestBench UI.
     */
    fun getStats(message: String): Stats {
        val zwCount = message.count { it == ZW_ZERO || it == ZW_ONE }
        val visibleCount = message.count { it !in ZW_CHARS }
        return Stats(
                totalLength = message.length,
                visibleChars = visibleCount,
                zwChars = zwCount,
                estimatedBytes = zwCount / 8,
                hasValidPayload = zwCount >= 8 && zwCount % 8 == 0
        )
    }

    data class Stats(
            val totalLength: Int,
            val visibleChars: Int,
            val zwChars: Int,
            val estimatedBytes: Int,
            val hasValidPayload: Boolean
    )
}
