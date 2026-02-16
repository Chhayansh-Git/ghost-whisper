package com.ghostwhisper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a channel encryption key.
 *
 * Each channel has a unique name, a derived Key ID, and a 256-bit AES key stored as Base64. Keys
 * never leave the device.
 */
@Entity(tableName = "keyring")
data class ChannelKey(
        /** Key ID: first 4 hex chars of SHA-256(channelName) */
        @PrimaryKey val keyId: String,

        /** Human-readable channel name (e.g., "Study Group") */
        val channelName: String,

        /** AES-256 key encoded as URL-safe Base64 (no padding) */
        val aesKeyBase64: String,

        /** Timestamp of key creation (epoch millis) */
        val createdAt: Long = System.currentTimeMillis(),

        /** Whether this channel is currently active */
        val isActive: Boolean = true,

        /** WhatsApp group name this channel is linked to (null = standalone) */
        val linkedGroupName: String? = null,

        /** Default cover message for this channel */
        val coverMessage: String = "Noted 👍",

        /** Firebase UID of the channel creator — only creator can dissolve */
        val creatorUid: String? = null
)
