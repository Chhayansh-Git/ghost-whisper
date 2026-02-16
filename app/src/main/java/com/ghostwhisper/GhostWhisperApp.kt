package com.ghostwhisper

import android.app.Application
import android.util.Log
import com.ghostwhisper.data.repository.UserRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ghost Whisper Application class.
 *
 * Initializes Firebase and updates user activity tracking.
 */
class GhostWhisperApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Update last active timestamp if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            appScope.launch {
                try {
                    UserRepository().updateLastActive(currentUser.uid)
                } catch (e: Exception) {
                    Log.w("GhostWhisperApp", "updateLastActive failed (network unavailable?)", e)
                }
            }
        }
    }
}
