package com.ghostwhisper.service

import com.ghostwhisper.crypto.PasswordCryptoHelper
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.repository.KeyringRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class KeyBackupService(
        private val keyringRepository: KeyringRepository,
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun backupKeys(uid: String, password: String) {
        val keys = keyringRepository.getAllActiveKeys()
        if (keys.isEmpty()) return

        val keysJsonArray = JSONArray()
        keys.forEach { key ->
            val json =
                    JSONObject().apply {
                        put("keyId", key.keyId)
                        put("channelName", key.channelName)
                        put("aesKeyBase64", key.aesKeyBase64)
                        put("createdAt", key.createdAt)
                        put("isActive", key.isActive)
                        put("linkedGroupName", key.linkedGroupName)
                        put("coverMessage", key.coverMessage)
                        put("creatorUid", key.creatorUid)
                    }
            keysJsonArray.put(json)
        }

        val encryptedData = PasswordCryptoHelper.encrypt(keysJsonArray.toString(), password)

        val backupData =
                hashMapOf(
                        "data" to encryptedData,
                        "timestamp" to Timestamp.now(),
                        "version" to 1,
                        "device" to android.os.Build.MODEL
                )

        firestore
                .collection("users")
                .document(uid)
                .collection("backups")
                .document("latest")
                .set(backupData)
                .await()
    }

    suspend fun restoreKeys(uid: String, password: String): Int {
        val snapshot =
                firestore
                        .collection("users")
                        .document(uid)
                        .collection("backups")
                        .document("latest")
                        .get()
                        .await()

        if (!snapshot.exists()) {
            throw Exception("No backup found")
        }

        val encryptedData = snapshot.getString("data") ?: throw Exception("Invalid backup data")

        try {
            val decryptedJsonString = PasswordCryptoHelper.decrypt(encryptedData, password)
            val jsonArray = JSONArray(decryptedJsonString)
            var restoredCount = 0

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val keyId = json.getString("keyId")

                val channelKey =
                        ChannelKey(
                                keyId = keyId,
                                channelName = json.getString("channelName"),
                                aesKeyBase64 = json.getString("aesKeyBase64"),
                                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                isActive = json.optBoolean("isActive", true),
                                linkedGroupName =
                                        if (json.has("linkedGroupName") &&
                                                        !json.isNull("linkedGroupName")
                                        )
                                                json.getString("linkedGroupName")
                                        else null,
                                coverMessage = json.optString("coverMessage", "Noted 👍"),
                                creatorUid =
                                        if (json.has("creatorUid") && !json.isNull("creatorUid"))
                                                json.getString("creatorUid")
                                        else null
                        )

                keyringRepository.restoreChannel(channelKey)
                restoredCount++
            }
            return restoredCount
        } catch (e: Exception) {
            throw Exception("Decryption failed. Incorrect password?")
        }
    }
}
