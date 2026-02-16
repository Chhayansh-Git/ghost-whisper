package com.ghostwhisper.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostwhisper.crypto.AESCrypto
import com.ghostwhisper.data.model.GhostPacket
import com.ghostwhisper.stegano.SteganoCodec
import com.ghostwhisper.ui.theme.*

/**
 * TestBench screen — Phase 1 milestone.
 *
 * Provides two text boxes for testing the encrypt/encode/decode/decrypt pipeline without needing
 * WhatsApp or a second device.
 */
@Composable
fun TestBenchScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // State
    var secretMessage by remember { mutableStateOf("") }
    var coverMessage by remember { mutableStateOf("Noted 👍") }
    var encodedOutput by remember { mutableStateOf("") }
    var decodedOutput by remember { mutableStateOf("") }
    var inputToDecode by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf<SteganoCodec.Stats?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Test key (generated once per session)
    val testKey = remember { AESCrypto.generateKey() }
    val testKeyId = remember { AESCrypto.deriveKeyId("TestBench") }
    var chaffingEnabled by remember { mutableStateOf(false) }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(DarkBackground)
                            .verticalScroll(scrollState)
                            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Header
        Text(
                text = "🧪 TestBench",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
        )
        Text(
                text = "End-to-end encryption pipeline test",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // ─── ENCRYPT SECTION ────────────────────────────────────
        SectionLabel("📤 ENCRYPT")

        OutlinedTextField(
                value = secretMessage,
                onValueChange = { secretMessage = it },
                label = { Text("Secret message") },
                placeholder = { Text("Type your secret message...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = textFieldColors()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
                value = coverMessage,
                onValueChange = { coverMessage = it },
                label = { Text("Cover message (visible text)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Chaffing toggle
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                    text = "Chaffing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
            )
            Switch(
                    checked = chaffingEnabled,
                    onCheckedChange = { chaffingEnabled = it },
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = GhostPurple,
                                    checkedTrackColor = GhostPurple.copy(alpha = 0.3f)
                            )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Encrypt button
        Button(
                onClick = {
                    errorMessage = null
                    try {
                        // Step 1: Encrypt
                        val encrypted = AESCrypto.encrypt(secretMessage, testKey)

                        // Step 2: Build packet
                        val packet =
                                GhostPacket(
                                        keyId = testKeyId,
                                        iv = encrypted.ivBase64(),
                                        ciphertext = encrypted.ciphertextBase64()
                                )

                        // Step 3: Encode to ZW
                        val zwPayload = SteganoCodec.encode(packet.toBytes())

                        // Step 4: Inject into cover message
                        var fullMessage = SteganoCodec.injectPayload(coverMessage, zwPayload)

                        // Step 5 (optional): Apply chaffing to the cover part
                        if (chaffingEnabled) {
                            fullMessage = SteganoCodec.chaff(fullMessage)
                        }

                        encodedOutput = fullMessage
                        stats = SteganoCodec.getStats(fullMessage)
                    } catch (e: Exception) {
                        errorMessage = "Encrypt error: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GhostPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = secretMessage.isNotBlank()
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Encrypt & Encode", fontWeight = FontWeight.SemiBold, color = DarkBackground)
        }

        // ─── OUTPUT SECTION ─────────────────────────────────────
        if (encodedOutput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("📊 ENCODED OUTPUT")

            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Show what the receiver sees (visible part only)
                    Text(
                            text = "👁 What others see:",
                            style = MaterialTheme.typography.labelMedium,
                            color = GhostPurple
                    )
                    Text(
                            text = SteganoCodec.stripZeroWidth(encodedOutput),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(vertical = 4.dp)
                    )

                    HorizontalDivider(
                            color = TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Stats
                    stats?.let {
                        Text(
                                text = "📈 Stats:",
                                style = MaterialTheme.typography.labelMedium,
                                color = GhostPurple
                        )
                        Text(
                                text =
                                        "Total chars: ${it.totalLength} | Visible: ${it.visibleChars} | Hidden: ${it.zwChars} | Payload: ${it.estimatedBytes} bytes",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Copy button
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(encodedOutput))
                                    Toast.makeText(
                                                    context,
                                                    "Copied (with hidden payload)",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                    "📋 Copy Full",
                                    color = GhostPurple,
                                    style = MaterialTheme.typography.labelMedium
                            )
                        }
                        OutlinedButton(
                                onClick = { inputToDecode = encodedOutput },
                                shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                    "⬇️ Send to Decrypt",
                                    color = GhostGreen,
                                    style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // ─── DECRYPT SECTION ────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("📥 DECRYPT")

        OutlinedTextField(
                value = inputToDecode,
                onValueChange = { inputToDecode = it },
                label = { Text("Paste encoded message here") },
                placeholder = { Text("Paste a message with hidden payload...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = textFieldColors()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
                onClick = {
                    errorMessage = null
                    decodedOutput = ""
                    try {
                        // Step 1: Extract payload
                        val zwPayload =
                                SteganoCodec.extractPayload(inputToDecode)
                                        ?: throw Exception("No hidden payload found")

                        // Step 2: Decode ZW → bytes → JSON
                        val payloadBytes = SteganoCodec.decode(zwPayload)
                        val packet = GhostPacket.fromBytes(payloadBytes)

                        // Step 3: Verify key ID matches
                        if (packet.keyId != testKeyId) {
                            throw Exception("Key ID mismatch: ${packet.keyId} ≠ $testKeyId")
                        }

                        // Step 4: Decrypt
                        val plaintext =
                                AESCrypto.decrypt(
                                        ciphertext = packet.ciphertextBytes(),
                                        key = testKey,
                                        iv = packet.ivBytes()
                                )

                        decodedOutput = plaintext
                    } catch (e: Exception) {
                        errorMessage = "Decrypt error: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GhostGreen),
                shape = RoundedCornerShape(12.dp),
                enabled = inputToDecode.isNotBlank()
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Decrypt & Decode", fontWeight = FontWeight.SemiBold, color = DarkBackground)
        }

        // ─── RESULT ─────────────────────────────────────────────
        if (decodedOutput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = GhostGreenDark.copy(alpha = 0.3f)
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "✅ Decrypted Message:",
                            style = MaterialTheme.typography.labelMedium,
                            color = GhostGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = decodedOutput,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                    )
                }
            }
        }

        // Error display
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GhostRed.copy(alpha = 0.15f))
            ) {
                Text(
                        text = "❌ $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostRed,
                        modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Test key info
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "🔑 Test Session Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                )
                Text(
                        text = "Key ID: $testKeyId",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                )
                Text(
                        text = "Key: ${AESCrypto.keyToBase64(testKey).take(20)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                )
                Text(
                        text = "⚠️ This key is ephemeral — generated fresh each session",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = GhostPurple,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun textFieldColors() =
        OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GhostPurple,
                unfocusedBorderColor = TextMuted,
                cursorColor = GhostPurple,
                focusedLabelColor = GhostPurple,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                unfocusedLabelColor = TextSecondary
        )
