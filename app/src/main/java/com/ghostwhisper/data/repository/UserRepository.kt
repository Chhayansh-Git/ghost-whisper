package com.ghostwhisper.data.repository

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Repository for user profile management in Firestore.
 *
 * Stores basic user details for future monetization. All data is keyed by Firebase Auth UID.
 */
class UserRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    companion object {
        /** Unique device identifier — generated once per install. */
        private val deviceId: String by lazy { UUID.randomUUID().toString() }
    }

    /**
     * Create or update the user profile in Firestore after successful auth.
     *
     * Uses merge to avoid overwriting fields set by previous sessions.
     */
    suspend fun createOrUpdateUser(user: FirebaseUser) {
        try {
            val userData =
                    hashMapOf(
                            "uid" to user.uid,
                            "displayName" to (user.displayName ?: "Ghost User"),
                            "phoneNumber" to user.phoneNumber,
                            "email" to user.email,
                            "deviceId" to deviceId,
                            "lastActiveAt" to FieldValue.serverTimestamp()
                    )

            // Only set createdAt if the document doesn't exist yet
            val docRef = usersCollection.document(user.uid)
            val doc = docRef.get().await()
            if (!doc.exists()) {
                userData["createdAt"] = FieldValue.serverTimestamp()
            }

            docRef.set(userData, SetOptions.merge()).await()
        } catch (e: Exception) {
            // Firestore write failed — auth still succeeds, profile syncs next time
            android.util.Log.w("UserRepository", "Profile sync failed: ${e.message}")
        }
    }

    /** Update the lastActiveAt timestamp (called on each app launch). */
    suspend fun updateLastActive(uid: String) {
        try {
            // Use set+merge instead of update — works even if doc doesn't exist yet
            usersCollection
                    .document(uid)
                    .set(
                            hashMapOf("lastActiveAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                    )
                    .await()
        } catch (e: Exception) {
            android.util.Log.w("UserRepository", "LastActive update failed: ${e.message}")
        }
    }

    /** Get user stats for future analytics/monetization. */
    suspend fun getUserProfile(uid: String): Map<String, Any>? {
        val doc = usersCollection.document(uid).get().await()
        return if (doc.exists()) doc.data else null
    }
    /** Update the user's display name. */
    suspend fun updateDisplayName(uid: String, newName: String) {
        usersCollection.document(uid).update("displayName", newName).await()
    }
}
