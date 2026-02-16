package com.ghostwhisper.data.repository

import com.ghostwhisper.crypto.AESCrypto
import com.ghostwhisper.data.db.KeyringDao
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.model.ChannelMember
import com.ghostwhisper.data.model.ContactSource
import com.ghostwhisper.data.model.KeyDeliveryStatus
import com.ghostwhisper.data.model.MemberRole
import kotlinx.coroutines.flow.Flow

/**
 * Repository for channel key and member management.
 *
 * Provides a clean API over the Room DAO and handles key generation, ID derivation, member
 * management, and group channel logic.
 */
class KeyringRepository(private val dao: KeyringDao) {

    /** Reactive stream of all active channel keys */
    val activeChannels: Flow<List<ChannelKey>> = dao.getAllActiveKeys()

    /** Reactive count of active channels */
    val channelCount: Flow<Int> = dao.getActiveChannelCount()

    // ─── Channel CRUD ────────────────────────────────────────────

    /**
     * Create a new channel with a fresh 256-bit AES key.
     *
     * @param channelName Human-readable name for the channel
     * @param linkedGroupName Optional WhatsApp group to link to
     * @param creatorUid Firebase UID of the channel creator
     * @return The created ChannelKey
     */
    suspend fun createChannel(
            channelName: String,
            linkedGroupName: String? = null,
            creatorUid: String? = null
    ): ChannelKey {
        val key = AESCrypto.generateKey()
        val keyId = AESCrypto.deriveKeyId(channelName)
        val keyBase64 = AESCrypto.keyToBase64(key)

        val channelKey =
                ChannelKey(
                        keyId = keyId,
                        channelName = channelName,
                        aesKeyBase64 = keyBase64,
                        linkedGroupName = linkedGroupName,
                        creatorUid = creatorUid
                )

        dao.insert(channelKey)
        return channelKey
    }

    /**
     * Import a channel key from an external source (QR code or deep link).
     *
     * @param channelName Channel name
     * @param keyBase64 Base64-encoded AES key
     * @return The imported ChannelKey
     */
    suspend fun importChannel(channelName: String, keyBase64: String): ChannelKey {
        val keyId = AESCrypto.deriveKeyId(channelName)

        val channelKey =
                ChannelKey(keyId = keyId, channelName = channelName, aesKeyBase64 = keyBase64)

        dao.insert(channelKey)
        return channelKey
    }

    /** Restore a full channel key object (including metadata) from backup. */
    suspend fun restoreChannel(channelKey: ChannelKey) {
        dao.insert(channelKey)
    }

    /** Find a channel key by its Key ID. Used during the Silent Fail decryption protocol. */
    suspend fun findByKeyId(keyId: String): ChannelKey? {
        return dao.getByKeyId(keyId)
    }

    /**
     * Get all active keys as a list (non-reactive). Used by AccessibilityService for batch decrypt
     * attempts.
     */
    suspend fun getAllActiveKeys(): List<ChannelKey> {
        return dao.getAllActiveKeysList()
    }

    /** Delete (deactivate) a channel. */
    suspend fun deleteChannel(keyId: String) {
        dao.deactivate(keyId)
    }

    /** Permanently delete a channel and its key. */
    suspend fun permanentlyDelete(keyId: String) {
        dao.deleteAllMembers(keyId)
        dao.delete(keyId)
    }

    /**
     * Dissolve a channel (creator-only action). Deactivates the channel and removes all members.
     *
     * @return true if dissolution was performed
     */
    suspend fun dissolveChannel(keyId: String, currentUserUid: String): Boolean {
        val channel = dao.getByKeyId(keyId) ?: return false
        if (channel.creatorUid != null && channel.creatorUid != currentUserUid) {
            return false // Only the creator can dissolve
        }
        dao.deactivate(keyId)
        return true
    }

    /**
     * Rotate a channel's key: generates a new key for the same channel name. The old key is
     * deactivated; a new key is inserted.
     *
     * @param channelName The channel to rotate
     * @return The new ChannelKey
     */
    suspend fun rotateKey(channelName: String): ChannelKey {
        val oldKeyId = AESCrypto.deriveKeyId(channelName)
        dao.deactivate(oldKeyId)
        return createChannel(channelName)
    }

    /**
     * Generate a shareable URI for a channel. Format:
     * ghostwhisper://join?key=<base64>&name=<channelName>
     */
    fun generateShareUri(channelKey: ChannelKey): String {
        val encodedName = android.net.Uri.encode(channelKey.channelName)
        val encodedKey = android.net.Uri.encode(channelKey.aesKeyBase64)
        return "https://ghostwhisper.app/join?key=$encodedKey&name=$encodedName"
    }

    // ─── Group channels ──────────────────────────────────────────

    /** Find all channels linked to a specific WhatsApp group. */
    suspend fun findChannelsByGroup(groupName: String): List<ChannelKey> {
        return dao.getByGroupName(groupName)
    }

    /** Update the cover message for a channel. */
    suspend fun updateCoverMessage(keyId: String, coverMessage: String) {
        dao.updateCoverMessage(keyId, coverMessage)
    }

    /** Link a channel to a WhatsApp group. */
    suspend fun linkToGroup(keyId: String, groupName: String?) {
        dao.updateLinkedGroup(keyId, groupName)
    }

    /** Check whether a channel with the given name already exists (active). */
    suspend fun channelNameExists(name: String): Boolean {
        return dao.getByChannelName(name) != null
    }

    /**
     * Check whether any other channel in the same group has an identical member set.
     *
     * @param groupName WhatsApp group name to compare within
     * @param excludeKeyId Key ID of the channel to exclude from comparison (self)
     * @param memberPhones Phone numbers of the proposed member set
     * @return true if a duplicate member set exists
     */
    suspend fun hasDuplicateMemberSet(
            groupName: String,
            excludeKeyId: String,
            memberPhones: Set<String>
    ): Boolean {
        val siblingChannels = dao.getByGroupName(groupName).filter { it.keyId != excludeKeyId }
        for (channel in siblingChannels) {
            val existingPhones =
                    dao.getMembersForChannelList(channel.keyId).map { it.phoneNumber }.toSet()
            if (existingPhones == memberPhones) return true
        }
        return false
    }

    // ─── Member management ───────────────────────────────────────

    /** Get members for a channel (reactive). */
    fun getMembers(channelKeyId: String): Flow<List<ChannelMember>> {
        return dao.getMembersForChannel(channelKeyId)
    }

    /** Get members pending key delivery. */
    suspend fun getPendingMembers(channelKeyId: String): List<ChannelMember> {
        return dao.getPendingMembers(channelKeyId)
    }

    /** Add a member to a channel. */
    suspend fun addMember(
            channelKeyId: String,
            contactName: String,
            phoneNumber: String,
            role: MemberRole = MemberRole.MEMBER,
            contactSource: ContactSource = ContactSource.MANUAL
    ): ChannelMember {
        val member =
                ChannelMember(
                        channelKeyId = channelKeyId,
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        role = role,
                        contactSource = contactSource
                )
        dao.insertMember(member)
        return member
    }

    /** Import multiple members at once (from contacts scan). */
    suspend fun importMembers(
            channelKeyId: String,
            contacts: List<Pair<String, String>>, // (name, phone)
            contactSource: ContactSource = ContactSource.SCANNED
    ) {
        val members =
                contacts.map { (name, phone) ->
                    ChannelMember(
                            channelKeyId = channelKeyId,
                            contactName = name,
                            phoneNumber = phone,
                            contactSource = contactSource
                    )
                }
        dao.insertMembers(members)
    }

    /** Update delivery status for a member. */
    suspend fun updateDeliveryStatus(
            memberId: Long,
            status: KeyDeliveryStatus,
            hasKey: Boolean = status == KeyDeliveryStatus.DELIVERED
    ) {
        dao.updateMemberDeliveryStatus(memberId, status, hasKey)
    }

    /** Remove a member from a channel. */
    suspend fun removeMember(memberId: Long) {
        dao.deleteMember(memberId)
    }

    /** Rename a channel. */
    suspend fun renameChannel(keyId: String, newName: String) {
        dao.renameChannel(keyId, newName)
    }

    /** Copy members from one channel to another. */
    suspend fun copyMembersToChannel(fromChannelKeyId: String, toChannelKeyId: String) {
        val members = dao.getMembersForChannelList(fromChannelKeyId)
        val copied =
                members.map {
                    it.copy(
                            id = 0,
                            channelKeyId = toChannelKeyId,
                            hasKey = false,
                            keyDeliveryStatus = KeyDeliveryStatus.PENDING
                    )
                }
        dao.insertMembers(copied)
    }
}
