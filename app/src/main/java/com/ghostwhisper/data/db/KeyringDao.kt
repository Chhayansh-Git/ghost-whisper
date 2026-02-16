package com.ghostwhisper.data.db

import androidx.room.*
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.model.ChannelMember
import com.ghostwhisper.data.model.KeyDeliveryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the Keyring and Members tables.
 *
 * Provides reactive queries via Flow for UI observation and suspend functions for write operations.
 */
@Dao
interface KeyringDao {

        // ─── Channel Keys ────────────────────────────────────────────

        /**
         * Get all active channel keys, ordered by creation date (newest first). Returns a Flow for
         * reactive UI updates.
         */
        @Query("SELECT * FROM keyring WHERE isActive = 1 ORDER BY createdAt DESC")
        fun getAllActiveKeys(): Flow<List<ChannelKey>>

        /** Get all keys (including inactive), ordered by creation date. */
        @Query("SELECT * FROM keyring ORDER BY createdAt DESC")
        fun getAllKeys(): Flow<List<ChannelKey>>

        /** Get a specific key by its Key ID. Used during decryption to find the matching key. */
        @Query("SELECT * FROM keyring WHERE keyId = :keyId AND isActive = 1 LIMIT 1")
        suspend fun getByKeyId(keyId: String): ChannelKey?

        /** Get a specific key by channel name. */
        @Query("SELECT * FROM keyring WHERE channelName = :channelName AND isActive = 1 LIMIT 1")
        suspend fun getByChannelName(channelName: String): ChannelKey?

        /**
         * Get all active keys as a plain list (non-reactive). Used by the Accessibility Service for
         * batch decryption attempts.
         */
        @Query("SELECT * FROM keyring WHERE isActive = 1")
        suspend fun getAllActiveKeysList(): List<ChannelKey>

        /** Get all channels linked to a specific WhatsApp group name. */
        @Query(
                "SELECT * FROM keyring WHERE linkedGroupName = :groupName AND isActive = 1 ORDER BY createdAt DESC"
        )
        suspend fun getByGroupName(groupName: String): List<ChannelKey>

        /** Insert a new channel key. Replaces on conflict (key rotation). */
        @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(channelKey: ChannelKey)

        /** Deactivate a channel (soft delete). */
        @Query("UPDATE keyring SET isActive = 0 WHERE keyId = :keyId")
        suspend fun deactivate(keyId: String)

        /** Hard delete a channel key. */
        @Query("DELETE FROM keyring WHERE keyId = :keyId") suspend fun delete(keyId: String)

        /** Get the count of active channels. */
        @Query("SELECT COUNT(*) FROM keyring WHERE isActive = 1")
        fun getActiveChannelCount(): Flow<Int>

        /** Update the cover message for a channel. */
        @Query("UPDATE keyring SET coverMessage = :coverMessage WHERE keyId = :keyId")
        suspend fun updateCoverMessage(keyId: String, coverMessage: String)

        /** Link a channel to a WhatsApp group. */
        @Query("UPDATE keyring SET linkedGroupName = :groupName WHERE keyId = :keyId")
        suspend fun updateLinkedGroup(keyId: String, groupName: String?)

        /** Rename a channel. */
        @Query("UPDATE keyring SET channelName = :newName WHERE keyId = :keyId")
        suspend fun renameChannel(keyId: String, newName: String)

        // ─── Channel Members ─────────────────────────────────────────

        /** Get all members of a channel. */
        @Query(
                "SELECT * FROM channel_members WHERE channelKeyId = :channelKeyId ORDER BY contactName ASC"
        )
        fun getMembersForChannel(channelKeyId: String): Flow<List<ChannelMember>>

        /** Get all members of a channel as a list (non-reactive). */
        @Query("SELECT * FROM channel_members WHERE channelKeyId = :channelKeyId")
        suspend fun getMembersForChannelList(channelKeyId: String): List<ChannelMember>

        /** Get members pending key delivery. */
        @Query("SELECT * FROM channel_members WHERE channelKeyId = :channelKeyId AND hasKey = 0")
        suspend fun getPendingMembers(channelKeyId: String): List<ChannelMember>

        /** Insert a new member. */
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertMember(member: ChannelMember)

        /** Insert multiple members at once. */
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertMembers(members: List<ChannelMember>)

        /** Update key delivery status for a member. */
        @Query(
                "UPDATE channel_members SET keyDeliveryStatus = :status, hasKey = :hasKey WHERE id = :memberId"
        )
        suspend fun updateMemberDeliveryStatus(
                memberId: Long,
                status: KeyDeliveryStatus,
                hasKey: Boolean
        )

        /** Delete a member from a channel. */
        @Query("DELETE FROM channel_members WHERE id = :memberId")
        suspend fun deleteMember(memberId: Long)

        /** Delete a member by phone number. */
        @Query(
                "DELETE FROM channel_members WHERE channelKeyId = :channelKeyId AND phoneNumber = :phoneNumber"
        )
        suspend fun deleteMemberByPhone(channelKeyId: String, phoneNumber: String)

        /** Delete all members of a channel. */
        @Query("DELETE FROM channel_members WHERE channelKeyId = :channelKeyId")
        suspend fun deleteAllMembers(channelKeyId: String)

        /** Get count of members for a channel. */
        @Query("SELECT COUNT(*) FROM channel_members WHERE channelKeyId = :channelKeyId")
        fun getMemberCount(channelKeyId: String): Flow<Int>
}
