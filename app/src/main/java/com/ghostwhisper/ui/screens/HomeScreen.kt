package com.ghostwhisper.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostwhisper.service.GhostWhisperService
import com.ghostwhisper.ui.theme.*
import com.ghostwhisper.ui.widget.FloatingWidgetService

/**
 * Home screen / Dashboard.
 *
 * Shows service status, quick actions, and onboarding guidance.
 */
@Composable
fun HomeScreen() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var refreshTrigger by remember { mutableIntStateOf(0) }

        // Force refresh on resume
        DisposableEffect(lifecycleOwner) {
                val observer =
                        androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                        refreshTrigger++
                                }
                        }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Read service state (depends on refreshTrigger to re-read on resume)
        val isServiceActive = remember(refreshTrigger) { GhostWhisperService.isServiceRunning }
        var isWidgetRunning by
                remember(refreshTrigger) { mutableStateOf(FloatingWidgetService.isRunning) }
        val activeChannel = GhostWhisperService.activeChannelName

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(DarkBackground)
                                .verticalScroll(scrollState)
                                .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Ghost icon
                Box(
                        modifier =
                                Modifier.size(100.dp)
                                        .clip(CircleShape)
                                        .background(
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        GhostPurple.copy(
                                                                                alpha = 0.3f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                )
                                        ),
                        contentAlignment = Alignment.Center
                ) { Text(text = "👻", fontSize = 56.sp) }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = "Ghost Whisper",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = "Security through Invisibility",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostPurple,
                        modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Service Status Card
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (isServiceActive) GhostGreen
                                                                else GhostRed
                                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text =
                                                        if (isServiceActive) "Service Active"
                                                        else "Service Inactive",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary
                                        )
                                        Text(
                                                text =
                                                        if (isServiceActive)
                                                                "Monitoring WhatsApp for encrypted messages"
                                                        else
                                                                "Enable accessibility service to start",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                        )
                                }
                                if (isServiceActive) {
                                        Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = GhostGreen
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Channel Card (if any)
                if (activeChannel != null) {
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = GhostPurpleDeep.copy(alpha = 0.5f)
                                        )
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text("🔒", fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Active Channel",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = GhostPurple
                                                )
                                                Text(
                                                        text = activeChannel,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = TextPrimary
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                }

                // Quick Actions
                Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                // Enable Accessibility Service
                if (!isServiceActive) {
                        QuickActionCard(
                                icon = "⚙️",
                                title = "Enable Accessibility Service",
                                subtitle = "Required for Ghost Whisper to work",
                                onClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                }

                // Enable Overlay Permission
                QuickActionCard(
                        icon = "🔲",
                        title = "Overlay Permission",
                        subtitle = "Required for floating widget & message overlay",
                        onClick = {
                                val intent =
                                        Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse(
                                                        "package:${context.packageName}"
                                                )
                                        )
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Start/Stop Widget
                QuickActionCard(
                        icon = "👻",
                        title = "Floating Widget",
                        subtitle = "Start or stop the floating ghost icon",
                        onClick = {
                                if (GhostWhisperService.isServiceRunning) {
                                        if (isWidgetRunning) {
                                                FloatingWidgetService.stop(context)
                                                isWidgetRunning = false
                                        } else {
                                                FloatingWidgetService.start(context)
                                                isWidgetRunning = true
                                        }
                                } else {
                                        android.widget.Toast.makeText(
                                                        context,
                                                        "Start Service first!",
                                                        android.widget.Toast.LENGTH_SHORT
                                                )
                                                .show()
                                }
                        }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Onboarding info
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                        text = "🚀 Getting Started",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GhostPurple
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OnboardingStep("1", "Enable Accessibility Service above")
                                OnboardingStep("2", "Grant Overlay permission")
                                OnboardingStep("3", "Create a channel in the Channels tab")
                                OnboardingStep(
                                        "4",
                                        "Share the channel key with friends via QR code"
                                )
                                OnboardingStep("5", "Open WhatsApp — the ghost icon appears!")
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))
        }
}

@Composable
private fun QuickActionCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
        Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(icon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                )
                                Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                )
                        }
                        Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted
                        )
                }
        }
}

@Composable
private fun OnboardingStep(number: String, text: String) {
        Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                        modifier =
                                Modifier.size(24.dp)
                                        .clip(CircleShape)
                                        .background(GhostPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = number,
                                style = MaterialTheme.typography.labelSmall,
                                color = GhostPurple,
                                fontWeight = FontWeight.Bold
                        )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                )
        }
}
