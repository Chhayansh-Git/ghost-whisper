package com.ghostwhisper.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A member of a Ghost Whisper channel.
 *
 * Tracks contact info, role, and key delivery status. A single contact can belong to multiple
 * channels (overlap allowed).
 */
@Entity(
        tableName = "channel_members",
        indices =
                [
                        Index(value = ["channelKeyId"]),
                        Index(value = ["channelKeyId", "phoneNumber"], unique = true)]
)
data class ChannelMember(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,

        /** Key ID of the channel this member belongs to. */
        val channelKeyId: String,

        /** Display name from contacts or manual entry. */
        val contactName: String,

        /** Phone number in international format (+91...). */
        val phoneNumber: String,

        /** Role in the channel. */
        val role: MemberRole = MemberRole.MEMBER,

        /** Whether this member has the channel key. */
        val hasKey: Boolean = false,

        /** How this member was added. */
        val contactSource: ContactSource = ContactSource.MANUAL,

        /** Status of key delivery. */
        val keyDeliveryStatus: KeyDeliveryStatus = KeyDeliveryStatus.PENDING,

        /** Timestamp of when the member was added. */
        val addedAt: Long = System.currentTimeMillis()
)

/** Member role within a Ghost Whisper channel. */
enum class MemberRole {
    ADMIN,
    MEMBER
}

/** How the member was imported into the channel. */
enum class ContactSource {
    /** Scanned from WhatsApp group info via Accessibility Service. */
    SCANNED,
    /** Matched from device contacts. */
    CONTACTS,
    /** Manually added by the user. */
    MANUAL
}

/** Status of key delivery to a member. */
enum class KeyDeliveryStatus {
    /** Key has not been sent yet. */
    PENDING,
    /** Key was sent via WhatsApp. */
    SENT_WHATSAPP,
    /** Key was sent via SMS. */
    SENT_SMS,
    /** Key delivery confirmed (member has joined). */
    DELIVERED,
    /** Delivery failed. */
    FAILED
}
