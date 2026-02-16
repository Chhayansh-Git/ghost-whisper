package com.ghostwhisper.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostwhisper.data.repository.AuthRepository
import com.ghostwhisper.data.repository.UserRepository
import com.ghostwhisper.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * Login screen with two auth options:
 * 1. Continue with Google (OAuth)
 * 2. Phone number + OTP
 *
 * No password fields — purely OAuth + OTP.
 */
@Composable
fun LoginScreen(
        authRepository: AuthRepository,
        userRepository: UserRepository,
        webClientId: String,
        onLoginSuccess: () -> Unit
) {
        val context = LocalContext.current
        val activity = context as Activity
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var phoneNumber by remember { mutableStateOf("") }
        var isGoogleLoading by remember { mutableStateOf(false) }
        var isPhoneLoading by remember { mutableStateOf(false) }
        var showOtpScreen by remember { mutableStateOf(false) }
        var verificationId by remember { mutableStateOf("") }

        // Google Sign-In launcher
        val googleSignInLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        scope.launch {
                                try {
                                        val task =
                                                GoogleSignIn.getSignedInAccountFromIntent(
                                                        result.data
                                                )
                                        val account = task.getResult(ApiException::class.java)
                                        val idToken =
                                                account.idToken ?: throw Exception("No ID token")

                                        val user = authRepository.signInWithGoogle(idToken)
                                        // Login succeeds immediately — profile sync happens in
                                        // background
                                        onLoginSuccess()
                                        // Background profile sync (fail-safe, won't affect login)
                                        try {
                                                userRepository.createOrUpdateUser(user)
                                        } catch (e: Exception) {
                                                Log.w("LoginScreen", "Profile sync failed", e)
                                        }
                                } catch (e: Exception) {
                                        isGoogleLoading = false
                                        snackbarHostState.showSnackbar(
                                                "Google sign-in failed: ${e.message}"
                                        )
                                }
                        }
                }

        // If showing OTP screen, navigate there
        if (showOtpScreen) {
                OtpVerificationScreen(
                        phoneNumber = phoneNumber,
                        verificationId = verificationId,
                        authRepository = authRepository,
                        userRepository = userRepository,
                        onVerified = onLoginSuccess,
                        onBack = { showOtpScreen = false }
                )
                return
        }

        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = GhostBlack) {
                padding ->
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(padding)
                                        .background(
                                                brush =
                                                        Brush.verticalGradient(
                                                                colors =
                                                                        listOf(
                                                                                GhostBlack,
                                                                                GhostDarkGray
                                                                        )
                                                        )
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                // Ghost icon
                                Text(text = "👻", fontSize = 64.sp)

                                Spacer(modifier = Modifier.height(8.dp))

                                // App name
                                Text(
                                        text = "Ghost Whisper",
                                        color = GhostGreen,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                )

                                // Tagline
                                Text(
                                        text = "Security through Invisibility",
                                        color = GhostLightGray,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // ─── Google Sign-In ──────────────────────────

                                Button(
                                        onClick = {
                                                isGoogleLoading = true
                                                val gso =
                                                        GoogleSignInOptions.Builder(
                                                                        GoogleSignInOptions
                                                                                .DEFAULT_SIGN_IN
                                                                )
                                                                .requestIdToken(webClientId)
                                                                .requestEmail()
                                                                .build()
                                                val googleSignInClient =
                                                        GoogleSignIn.getClient(context, gso)
                                                googleSignInLauncher.launch(
                                                        googleSignInClient.signInIntent
                                                )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface
                                                ),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isGoogleLoading && !isPhoneLoading
                                ) {
                                        if (isGoogleLoading) {
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = GhostGreen,
                                                        strokeWidth = 2.dp
                                                )
                                        } else {
                                                Text(
                                                        text = "Continue with Google",
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.SemiBold
                                                )
                                        }
                                }

                                // ─── Divider ─────────────────────────────────

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                color = GhostMediumGray
                                        )
                                        Text(
                                                text = "  or  ",
                                                color = GhostLightGray,
                                                fontSize = 12.sp
                                        )
                                        HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                color = GhostMediumGray
                                        )
                                }

                                // ─── Phone Number ────────────────────────────

                                OutlinedTextField(
                                        value = phoneNumber,
                                        onValueChange = { phoneNumber = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Phone number") },
                                        placeholder = { Text("+91 98765 43210") },
                                        leadingIcon = {
                                                Icon(Icons.Default.Phone, contentDescription = null)
                                        },
                                        keyboardOptions =
                                                KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostGreen,
                                                        unfocusedBorderColor = GhostMediumGray,
                                                        focusedLabelColor = GhostGreen,
                                                        cursorColor = GhostGreen
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                        onClick = {
                                                if (phoneNumber.isBlank()) {
                                                        scope.launch {
                                                                snackbarHostState.showSnackbar(
                                                                        "Enter a phone number"
                                                                )
                                                        }
                                                        return@Button
                                                }

                                                isPhoneLoading = true
                                                val formattedNumber =
                                                        if (phoneNumber.startsWith("+")) phoneNumber
                                                        else "+91$phoneNumber"

                                                authRepository.sendOtp(
                                                        phoneNumber = formattedNumber,
                                                        activity = activity,
                                                        onCodeSent = { vId ->
                                                                verificationId = vId
                                                                isPhoneLoading = false
                                                                showOtpScreen = true
                                                        },
                                                        onVerificationCompleted = { credential ->
                                                                // Auto-verification (same device
                                                                // SMS)
                                                                isPhoneLoading = false // Reset UI
                                                                // immediately
                                                                scope.launch {
                                                                        try {
                                                                                val user =
                                                                                        authRepository
                                                                                                .signInWithPhoneCredential(
                                                                                                        credential
                                                                                                )
                                                                                // Login succeeds
                                                                                // immediately
                                                                                onLoginSuccess()
                                                                                // Background
                                                                                // profile sync
                                                                                try {
                                                                                        userRepository
                                                                                                .createOrUpdateUser(
                                                                                                        user
                                                                                                )
                                                                                } catch (
                                                                                        _:
                                                                                                Exception) {}
                                                                        } catch (e: Exception) {
                                                                                snackbarHostState
                                                                                        .showSnackbar(
                                                                                                "Auto-verify failed: ${e.message}"
                                                                                        )
                                                                        }
                                                                }
                                                        },
                                                        onVerificationFailed = { e ->
                                                                isPhoneLoading = false
                                                                scope.launch {
                                                                        snackbarHostState
                                                                                .showSnackbar(
                                                                                        "OTP failed: ${e.message}"
                                                                                )
                                                                }
                                                        }
                                                )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = GhostGreen
                                                ),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isGoogleLoading && !isPhoneLoading
                                ) {
                                        if (isPhoneLoading) {
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = GhostBlack,
                                                        strokeWidth = 2.dp
                                                )
                                        } else {
                                                Text(
                                                        text = "Send OTP",
                                                        color = GhostBlack,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Privacy note
                                Text(
                                        text =
                                                "No passwords. No tracking.\nYour keys never leave your device.",
                                        color = GhostMediumGray,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                )
                        }
                }
        }
}
