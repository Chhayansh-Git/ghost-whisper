package com.ghostwhisper.service

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class BiometricHelper(private val context: Context) {

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt =
                BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult
                            ) {
                                super.onAuthenticationSucceeded(result)
                                onSuccess()
                            }

                            override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence
                            ) {
                                super.onAuthenticationError(errorCode, errString)
                                // If user cancels, we treat it as failure
                                onFailure()
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                // Biometric is valid but not recognized (e.g. wrong finger)
                                // We can let the user try again, but for now we don't trigger
                                // onFailure immediately
                                // The prompt stays open usually.
                            }
                        }
                )

        val promptInfo =
                BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Ghost Whisper Locked")
                        .setSubtitle("Authenticate to access")
                        .setNegativeButtonText("Cancel")
                        .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
