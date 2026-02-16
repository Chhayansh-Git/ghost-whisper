package com.ghostwhisper.data.model

import java.util.Base64
import org.json.JSONObject

/**
 * Represents the Ghost Whisper packet structure.
 *
 * Wire format (JSON → ZW-encoded): { "v": 1, // Protocol version "kid": "8f3a", // Key ID (4 hex
 * chars from SHA-256 of channel name) "iv": "x9s8f...", // Base64-encoded 12-byte IV "ct":
 * "U2FsdGVk..." // Base64-encoded ciphertext + GCM auth tag }
 */
data class GhostPacket(
        val version: Int = CURRENT_VERSION,
        val keyId: String,
        val iv: String, // Base64-encoded
        val ciphertext: String // Base64-encoded
) {
    companion object {
        const val CURRENT_VERSION = 1

        private const val KEY_V = "v"
        private const val KEY_KID = "kid"
        private const val KEY_IV = "iv"
        private const val KEY_CT = "ct"

        /**
         * Deserialize a GhostPacket from JSON string.
         *
         * @param json JSON string representation
         * @return Parsed GhostPacket
         * @throws org.json.JSONException if JSON is malformed
         */
        fun fromJson(json: String): GhostPacket {
            val obj = JSONObject(json)
            return GhostPacket(
                    version = obj.optInt(KEY_V, CURRENT_VERSION),
                    keyId = obj.getString(KEY_KID),
                    iv = obj.getString(KEY_IV),
                    ciphertext = obj.getString(KEY_CT)
            )
        }

        /** Deserialize a GhostPacket from raw bytes (UTF-8 JSON). */
        fun fromBytes(bytes: ByteArray): GhostPacket {
            return fromJson(String(bytes, Charsets.UTF_8))
        }
    }

    /** Serialize this packet to JSON string. */
    fun toJson(): String {
        return JSONObject()
                .apply {
                    put(KEY_V, version)
                    put(KEY_KID, keyId)
                    put(KEY_IV, iv)
                    put(KEY_CT, ciphertext)
                }
                .toString()
    }

    /** Serialize this packet to raw bytes (UTF-8 JSON). */
    fun toBytes(): ByteArray {
        return toJson().toByteArray(Charsets.UTF_8)
    }

    /** Get the IV as raw bytes (decoded from Base64). */
    fun ivBytes(): ByteArray {
        return Base64.getUrlDecoder().decode(iv)
    }

    /** Get the ciphertext as raw bytes (decoded from Base64). */
    fun ciphertextBytes(): ByteArray {
        return Base64.getUrlDecoder().decode(ciphertext)
    }
}
