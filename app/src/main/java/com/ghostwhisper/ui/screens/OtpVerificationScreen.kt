package com.ghostwhisper.ui.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OtpVerificationScreen"
private const val OTP_LENGTH = 6
private const val RESEND_TIMEOUT_SECONDS = 60

/**
 * OTP verification screen.
 *
 * Shows a 6-digit code input with:
 * - Auto-focus on the text field
 * - 60-second countdown for resend
 * - Auto-submit when 6 digits are entered
 * - In-screen OTP resend (no navigation needed)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
        phoneNumber: String,
        verificationId: String,
        authRepository: AuthRepository,
        userRepository: UserRepository,
        onVerified: () -> Unit,
        onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    var otpCode by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(RESEND_TIMEOUT_SECONDS) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Mutable verification ID — updated when OTP is resent
    var currentVerificationId by remember { mutableStateOf(verificationId) }

    // Countdown timer for resend
    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1000)
            resendCountdown--
        }
    }

    // Auto-focus on mount
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    /**
     * Verify OTP and handle login. Profile sync is non-blocking — login succeeds even if Firestore
     * is down.
     */
    fun verifyOtp(code: String) {
        if (code.length != OTP_LENGTH || isVerifying) return

        isVerifying = true
        errorMessage = null

        scope.launch {
            try {
                val user = authRepository.verifyOtp(currentVerificationId, code)
                Log.i(TAG, "OTP verified successfully for ${user.uid}")

                // Login succeeds immediately
                onVerified()

                // Background profile sync — logged but non-blocking
                try {
                    userRepository.createOrUpdateUser(user)
                    Log.d(TAG, "Profile synced for ${user.uid}")
                } catch (e: Exception) {
                    Log.w(TAG, "Background profile sync failed (non-critical): ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "OTP verification failed: ${e.message}")
                isVerifying = false
                errorMessage =
                        when {
                            e.message?.contains("expired", ignoreCase = true) == true ->
                                    "OTP expired. Please request a new one."
                            e.message?.contains("invalid", ignoreCase = true) == true ->
                                    "Invalid OTP. Please check and try again."
                            else -> "Verification failed. Please try again."
                        }
                otpCode = ""
            }
        }
    }

    /**
     * Resend OTP in-screen without navigating back. Uses Firebase's sendOtp directly and updates
     * the verification ID.
     */
    fun resendOtp() {
        if (isResending) return

        isResending = true
        val formattedNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+91$phoneNumber"

        authRepository.sendOtp(
                phoneNumber = formattedNumber,
                activity = activity,
                onCodeSent = { newVerificationId ->
                    currentVerificationId = newVerificationId
                    isResending = false
                    resendCountdown = RESEND_TIMEOUT_SECONDS
                    otpCode = ""
                    errorMessage = null
                    Toast.makeText(context, "OTP resent to $phoneNumber", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "OTP resent successfully")
                },
                onVerificationCompleted = { credential ->
                    // Auto-verification (same device SMS)
                    isResending = false
                    scope.launch {
                        try {
                            val user = authRepository.signInWithPhoneCredential(credential)
                            Log.i(TAG, "Auto-verified on resend for ${user.uid}")
                            onVerified()
                            try {
                                userRepository.createOrUpdateUser(user)
                            } catch (e: Exception) {
                                Log.w(TAG, "Background profile sync failed: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Auto-verify on resend failed: ${e.message}")
                            snackbarHostState.showSnackbar("Verification failed: ${e.message}")
                        }
                    }
                },
                onVerificationFailed = { e ->
                    isResending = false
                    Log.e(TAG, "Resend OTP failed: ${e.message}")
                    scope.launch {
                        snackbarHostState.showSnackbar("Failed to resend: ${e.message}")
                    }
                }
        )
    }

    // Auto-submit when 6 digits entered
    LaunchedEffect(otpCode) {
        if (otpCode.length == OTP_LENGTH && !isVerifying) {
            verifyOtp(otpCode)
        }
    }

    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = GhostLightGray
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = GhostBlack)
                )
            },
            containerColor = GhostBlack
    ) { padding ->
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .background(
                                        brush =
                                                Brush.verticalGradient(
                                                        colors = listOf(GhostBlack, GhostDarkGray)
                                                )
                                ),
                contentAlignment = Alignment.Center
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Lock icon
                Text(text = "🔐", fontSize = 48.sp)

                // Title
                Text(
                        text = "Verify OTP",
                        color = GhostGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                )

                // Subtitle
                Text(
                        text = "Enter the 6-digit code sent to\n$phoneNumber",
                        color = GhostLightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // OTP Input
                OutlinedTextField(
                        value = otpCode,
                        onValueChange = { newValue ->
                            if (newValue.length <= OTP_LENGTH && newValue.all { it.isDigit() }) {
                                otpCode = newValue
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle =
                                LocalTextStyle.current.copy(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 16.sp
                                ),
                        placeholder = {
                            Text(
                                    text = "• • • • • •",
                                    fontSize = 32.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GhostGreen,
                                        unfocusedBorderColor = GhostMediumGray,
                                        cursorColor = GhostGreen,
                                        errorBorderColor = GhostRed
                                ),
                        isError = errorMessage != null,
                        shape = RoundedCornerShape(12.dp)
                )

                // Error message
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = GhostRed, fontSize = 13.sp)
                }

                // Verifying indicator
                if (isVerifying) {
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GhostGreen,
                                strokeWidth = 2.dp
                        )
                        Text(text = "Verifying...", color = GhostGreen, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Resend button
                if (resendCountdown > 0) {
                    Text(
                            text = "Resend OTP in ${resendCountdown}s",
                            color = GhostMediumGray,
                            fontSize = 13.sp
                    )
                } else {
                    TextButton(onClick = { resendOtp() }, enabled = !isResending) {
                        if (isResending) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = GhostGreen,
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                    text = "Resend OTP",
                                    color = GhostGreen,
                                    fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Verify button (manual)
                Button(
                        onClick = { verifyOtp(otpCode) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GhostGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = otpCode.length == OTP_LENGTH && !isVerifying
                ) {
                    Text(
                            text = "Verify",
                            color = GhostBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                    )
                }
            }
        }
    }
}
