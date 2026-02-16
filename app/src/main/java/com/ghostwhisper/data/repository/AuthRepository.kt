package com.ghostwhisper.data.repository

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Authentication.
 *
 * Supports two auth methods — no passwords anywhere:
 * 1. Google Sign-In (OAuth)
 * 2. Phone number OTP verification
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Currently signed-in user, null if not authenticated. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** True if a user is signed in. */
    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    // ─── Google Sign-In ─────────────────────────────────────────────

    /**
     * Complete Google Sign-In with the ID token from Credential Manager.
     *
     * @param idToken The Google ID token
     * @return The authenticated FirebaseUser
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw IllegalStateException("Google sign-in returned null user")
    }

    // ─── Phone OTP ──────────────────────────────────────────────────

    /**
     * Send OTP to a phone number.
     *
     * @param phoneNumber Full international format (e.g., "+911234567890")
     * @param activity Activity for reCAPTCHA verification
     * @param onCodeSent Callback with verificationId for later use
     * @param onVerificationCompleted Auto-verification callback (instant verify on same device)
     * @param onVerificationFailed Error callback
     */
    fun sendOtp(
            phoneNumber: String,
            activity: Activity,
            onCodeSent: (verificationId: String) -> Unit,
            onVerificationCompleted: (PhoneAuthCredential) -> Unit,
            onVerificationFailed: (Exception) -> Unit
    ) {
        val options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(
                                object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                    override fun onVerificationCompleted(
                                            credential: PhoneAuthCredential
                                    ) {
                                        onVerificationCompleted(credential)
                                    }

                                    override fun onVerificationFailed(
                                            e: com.google.firebase.FirebaseException
                                    ) {
                                        onVerificationFailed(e)
                                    }

                                    override fun onCodeSent(
                                            verificationId: String,
                                            token: PhoneAuthProvider.ForceResendingToken
                                    ) {
                                        onCodeSent(verificationId)
                                    }
                                }
                        )
                        .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Verify the OTP code entered by the user.
     *
     * @param verificationId From the onCodeSent callback
     * @param code The 6-digit OTP
     * @return The authenticated FirebaseUser
     */
    suspend fun verifyOtp(verificationId: String, code: String): FirebaseUser {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInWithPhoneCredential(credential)
    }

    /** Sign in with a phone auth credential (used by both manual OTP and auto-verification). */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): FirebaseUser {
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw IllegalStateException("Phone sign-in returned null user")
    }

    // ─── General ────────────────────────────────────────────────────

    /** Sign out from Firebase Auth. */
    fun signOut() {
        auth.signOut()
    }
}
