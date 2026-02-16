package com.ghostwhisper.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ghostwhisper.data.repository.AuthRepository
import com.ghostwhisper.data.repository.UserRepository
import com.ghostwhisper.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Settings screen for Ghost Whisper.
 *
 * Configures security options, profile, cover messages, and appearance.
 */
@Composable
fun SettingsScreen(navController: NavController, onSignOut: () -> Unit) {
        val context = LocalContext.current
        val activity = context as? androidx.fragment.app.FragmentActivity
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        val authRepository = remember { AuthRepository() }
        val userRepository = remember { UserRepository() }
        val settingsRepository = remember {
                com.ghostwhisper.data.repository.SettingsRepository(context)
        }
        val biometricHelper = remember {
                try {
                        com.ghostwhisper.service.BiometricHelper(context)
                } catch (e: Exception) {
                        Log.w("SettingsScreen", "BiometricHelper init failed", e)
                        null
                }
        }

        val keyringRepository = remember {
                com.ghostwhisper.data.repository.KeyringRepository(
                        com.ghostwhisper.data.db.KeyringDatabase.getInstance(context).keyringDao()
                )
        }
        val keyBackupService = remember {
                com.ghostwhisper.service.KeyBackupService(keyringRepository)
        }

        val currentUser = authRepository.currentUser
        // Load extra profile data from Firestore — fail gracefully if unreachable
        val userProfile by
                produceState<Map<String, Any>?>(initialValue = null, currentUser) {
                        currentUser?.let {
                                value =
                                        try {
                                                userRepository.getUserProfile(it.uid)
                                        } catch (e: Exception) {
                                                Log.w(
                                                        "SettingsScreen",
                                                        "Firestore profile fetch failed",
                                                        e
                                                )
                                                null
                                        }
                        }
                }

        // Local state for display name (optimistic update)
        var displayName by
                remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "Ghost User") }
        var showEditProfileDialog by remember { mutableStateOf(false) }

        // Settings state
        var appLockEnabled by remember { mutableStateOf(settingsRepository.appLockEnabled) }
        var chaffingEnabled by remember { mutableStateOf(settingsRepository.chaffingEnabled) }
        var clipboardGuardEnabled by remember {
                mutableStateOf(settingsRepository.clipboardGuardEnabled)
        }
        var overlayTimeout by remember { mutableFloatStateOf(settingsRepository.overlayTimeout) }

        // Cover messages
        var coverMessages by remember { mutableStateOf(settingsRepository.getCoverMessages()) }
        var showAddCoverDialog by remember { mutableStateOf(false) }
        var showBackupDialog by remember { mutableStateOf(false) }
        var showRestoreDialog by remember { mutableStateOf(false) }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(DarkBackground)
                                .verticalScroll(scrollState)
                                .padding(24.dp)
        ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                        text = "⚙️ Settings",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Profile Section ─────────────────────────────────────
                if (currentUser != null) {
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                                Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Avatar Placeholder
                                        Box(
                                                modifier =
                                                        Modifier.size(60.dp)
                                                                .background(
                                                                        GhostPurple,
                                                                        CircleShape
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = displayName.take(1).uppercase(),
                                                        fontSize = 24.sp,
                                                        color = DarkBackground,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = displayName,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                color = TextPrimary,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                        IconButton(
                                                                onClick = {
                                                                        showEditProfileDialog = true
                                                                },
                                                                modifier =
                                                                        Modifier.size(24.dp)
                                                                                .padding(
                                                                                        start = 8.dp
                                                                                )
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Edit,
                                                                        contentDescription =
                                                                                "Edit Name",
                                                                        tint = GhostPurple,
                                                                        modifier =
                                                                                Modifier.size(14.dp)
                                                                )
                                                        }
                                                }
                                                Text(
                                                        text = currentUser.email
                                                                        ?: currentUser.phoneNumber
                                                                                ?: "No contact info",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary
                                                )
                                                Text(
                                                        text =
                                                                "UID: ...${currentUser.uid.takeLast(6)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextMuted
                                                )
                                        }
                                }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                }

                // ─── Security Section ────────────────────────────────────
                SectionHeader("🛡 Security")

                SettingsToggle(
                        title = "App Lock",
                        subtitle = "Require biometric or PIN to open settings",
                        checked = appLockEnabled,
                        onCheckedChange = { isChecked ->
                                if (activity == null || biometricHelper == null)
                                        return@SettingsToggle

                                if (isChecked) {
                                        // Enable
                                        if (biometricHelper.canAuthenticate()) {
                                                biometricHelper.authenticate(
                                                        activity,
                                                        onSuccess = {
                                                                appLockEnabled = true
                                                                settingsRepository.appLockEnabled =
                                                                        true
                                                        },
                                                        onFailure = {
                                                                android.widget.Toast.makeText(
                                                                                context,
                                                                                "Authentication failed",
                                                                                android.widget.Toast
                                                                                        .LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        }
                                                )
                                        } else {
                                                android.widget.Toast.makeText(
                                                                context,
                                                                "Biometric setup required on device settings",
                                                                android.widget.Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                        }
                                } else {
                                        // Disable
                                        biometricHelper.authenticate(
                                                activity,
                                                onSuccess = {
                                                        appLockEnabled = false
                                                        settingsRepository.appLockEnabled = false
                                                },
                                                onFailure = {
                                                        android.widget.Toast.makeText(
                                                                        context,
                                                                        "Authentication failed",
                                                                        android.widget.Toast
                                                                                .LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        )
                                }
                        }
                )

                SettingsToggle(
                        title = "Chaffing Mode",
                        subtitle =
                                "Inject random invisible chars into ALL messages to defeat traffic analysis",
                        checked = chaffingEnabled,
                        onCheckedChange = {
                                chaffingEnabled = it
                                settingsRepository.chaffingEnabled = it
                        }
                )

                SettingsToggle(
                        title = "Clipboard Guard",
                        subtitle = "Automatically strip hidden data when messages are copied",
                        checked = clipboardGuardEnabled,
                        onCheckedChange = {
                                clipboardGuardEnabled = it
                                settingsRepository.clipboardGuardEnabled = it
                        }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Cover Messages Section ──────────────────────────────
                SectionHeader("💬 Cover Messages")
                Text(
                        text =
                                "Pre-configured cover messages that appear alongside encrypted content",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                )

                coverMessages.forEachIndexed { index, message ->
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 12.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                                onClick = {
                                                        settingsRepository.removeCoverMessage(
                                                                message
                                                        )
                                                        coverMessages =
                                                                settingsRepository
                                                                        .getCoverMessages()
                                                },
                                                modifier = Modifier.size(32.dp)
                                        ) { Text("✕", color = TextMuted, fontSize = 14.sp) }
                                }
                        }
                }

                TextButton(
                        onClick = { showAddCoverDialog = true },
                        modifier = Modifier.padding(top = 4.dp)
                ) { Text("+ Add Cover Message", color = GhostPurple) }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Cloud Backup Section ────────────────────────────────
                SectionHeader("🔐 Advanced Tools")

                SettingsItem(
                        icon = Icons.Default.Image,
                        title = "Steganography Lab",
                        subtitle = "Hide messages inside images",
                        onClick = { navController.navigate("steganography") }
                )

                HorizontalDivider(
                        color = DarkSurface,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                )

                SectionHeader("☁️ Cloud Backup")

                Text(
                        text =
                                "Securely backup and sync your channel keys. Encrypted with your password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        OutlinedButton(
                                onClick = { showBackupDialog = true },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = GhostPurple
                                        )
                        ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Backup")
                        }

                        OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = GhostPurple
                                        )
                        ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore")
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Account Section ─────────────────────────────────────
                SectionHeader("👤 Account")

                OutlinedButton(
                        onClick = {
                                authRepository.signOut()
                                onSignOut()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GhostRed),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // ─── About Section ───────────────────────────────────────
                SectionHeader("ℹ️ About")

                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                InfoRow("Version", "1.0.0")
                                InfoRow("License", "MIT License")
                                InfoRow("Encryption", "AES-256-GCM")
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))
        }

        // ─── Dialogs ─────────────────────────────────────────────────

        if (showAddCoverDialog) {
                var newMessage by remember { mutableStateOf("") }
                AlertDialog(
                        onDismissRequest = { showAddCoverDialog = false },
                        containerColor = DarkSurface,
                        title = { Text("Add Cover Message", color = TextPrimary) },
                        text = {
                                OutlinedTextField(
                                        value = newMessage,
                                        onValueChange = { newMessage = it },
                                        label = { Text("Cover message text") },
                                        placeholder = { Text("e.g., \"Sounds good\"") },
                                        singleLine = true,
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        cursorColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple,
                                                        unfocusedTextColor = TextPrimary,
                                                        focusedTextColor = TextPrimary
                                                ),
                                        modifier = Modifier.fillMaxWidth()
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                if (newMessage.isNotBlank()) {
                                                        settingsRepository.addCoverMessage(
                                                                newMessage.trim()
                                                        )
                                                        coverMessages =
                                                                settingsRepository
                                                                        .getCoverMessages()
                                                        showAddCoverDialog = false
                                                }
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = GhostPurple
                                                ),
                                        enabled = newMessage.isNotBlank()
                                ) { Text("Add", color = DarkBackground) }
                        },
                        dismissButton = {
                                TextButton(onClick = { showAddCoverDialog = false }) {
                                        Text("Cancel", color = TextSecondary)
                                }
                        }
                )
        }

        if (showBackupDialog) {
                var password by remember { mutableStateOf("") }
                var isProcessing by remember { mutableStateOf(false) }

                AlertDialog(
                        onDismissRequest = { showBackupDialog = false },
                        containerColor = DarkSurface,
                        title = { Text("Backup Keys", color = TextPrimary) },
                        text = {
                                Column {
                                        Text(
                                                "Enter a strong password to encrypt your backup. You MUST remember this password to restore.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                                value = password,
                                                onValueChange = { password = it },
                                                label = { Text("Backup Password") },
                                                singleLine = true,
                                                visualTransformation =
                                                        androidx.compose.ui.text.input
                                                                .PasswordVisualTransformation(),
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = GhostPurple,
                                                                focusedLabelColor = GhostPurple,
                                                                cursorColor = GhostPurple
                                                        )
                                        )
                                }
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                isProcessing = true
                                                scope.launch {
                                                        try {
                                                                currentUser?.let {
                                                                        keyBackupService.backupKeys(
                                                                                it.uid,
                                                                                password
                                                                        )
                                                                        android.widget.Toast
                                                                                .makeText(
                                                                                        context,
                                                                                        "Backup successful! ☁️",
                                                                                        android.widget
                                                                                                .Toast
                                                                                                .LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                        showBackupDialog = false
                                                                }
                                                        } catch (e: Exception) {
                                                                android.widget.Toast.makeText(
                                                                                context,
                                                                                "Backup failed: ${e.message}",
                                                                                android.widget.Toast
                                                                                        .LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        } finally {
                                                                isProcessing = false
                                                        }
                                                }
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = GhostPurple
                                                ),
                                        enabled = password.isNotBlank() && !isProcessing
                                ) {
                                        if (isProcessing)
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = DarkBackground
                                                )
                                        else Text("Backup")
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showBackupDialog = false }) {
                                        Text("Cancel", color = TextSecondary)
                                }
                        }
                )
        }

        if (showRestoreDialog) {
                var password by remember { mutableStateOf("") }
                var isProcessing by remember { mutableStateOf(false) }

                AlertDialog(
                        onDismissRequest = { showRestoreDialog = false },
                        containerColor = DarkSurface,
                        title = { Text("Restore Keys", color = TextPrimary) },
                        text = {
                                Column {
                                        Text(
                                                "Enter your backup password to decrypt and restore keys.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                                value = password,
                                                onValueChange = { password = it },
                                                label = { Text("Backup Password") },
                                                singleLine = true,
                                                visualTransformation =
                                                        androidx.compose.ui.text.input
                                                                .PasswordVisualTransformation(),
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = GhostPurple,
                                                                focusedLabelColor = GhostPurple,
                                                                cursorColor = GhostPurple
                                                        )
                                        )
                                }
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                isProcessing = true
                                                scope.launch {
                                                        try {
                                                                currentUser?.let {
                                                                        val count =
                                                                                keyBackupService
                                                                                        .restoreKeys(
                                                                                                it.uid,
                                                                                                password
                                                                                        )
                                                                        android.widget.Toast
                                                                                .makeText(
                                                                                        context,
                                                                                        "Restored $count keys! 🔑",
                                                                                        android.widget
                                                                                                .Toast
                                                                                                .LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                        showRestoreDialog = false
                                                                }
                                                        } catch (e: Exception) {
                                                                android.widget.Toast.makeText(
                                                                                context,
                                                                                "Restore failed: ${e.message}",
                                                                                android.widget.Toast
                                                                                        .LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        } finally {
                                                                isProcessing = false
                                                        }
                                                }
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = GhostPurple
                                                ),
                                        enabled = password.isNotBlank() && !isProcessing
                                ) {
                                        if (isProcessing)
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = DarkBackground
                                                )
                                        else Text("Restore")
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRestoreDialog = false }) {
                                        Text("Cancel", color = TextSecondary)
                                }
                        }
                )
        }

        if (showEditProfileDialog) {
                var newName by remember { mutableStateOf(displayName) }
                AlertDialog(
                        onDismissRequest = { showEditProfileDialog = false },
                        containerColor = DarkSurface,
                        title = { Text("Edit Profile Name") },
                        text = {
                                OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Display Name") },
                                        singleLine = true,
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple
                                                )
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                scope.launch {
                                                        currentUser?.let {
                                                                userRepository.updateDisplayName(
                                                                        it.uid,
                                                                        newName
                                                                )
                                                                displayName =
                                                                        newName // Optimistic update
                                                        }
                                                        showEditProfileDialog = false
                                                }
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = GhostPurple
                                                )
                                ) { Text("Save") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showEditProfileDialog = false }) {
                                        Text("Cancel")
                                }
                        }
                )
        }
}

@Composable
private fun SectionHeader(title: String) {
        Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GhostPurple,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
        )
}

@Composable
private fun SettingsToggle(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                                checked = checked,
                                onCheckedChange = onCheckedChange,
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = GhostPurple,
                                                checkedTrackColor = GhostPurple.copy(alpha = 0.3f)
                                        )
                        )
                }
        }
}

@Composable
private fun InfoRow(label: String, value: String) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                )
                Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                )
        }
}

@Composable
private fun SettingsItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit
) {
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(onClick = onClick),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(imageVector = icon, contentDescription = null, tint = GhostPurple)
                        Spacer(modifier = Modifier.width(16.dp))
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
                                imageVector =
                                        androidx.compose.material.icons.Icons.AutoMirrored.Filled
                                                .ArrowForward,
                                contentDescription = null,
                                tint = TextSecondary
                        )
                }
        }
}
