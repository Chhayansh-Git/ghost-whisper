package com.ghostwhisper.ui.screens

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostwhisper.data.db.KeyringDatabase
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.model.KeyDeliveryStatus
import com.ghostwhisper.data.repository.KeyringRepository
import com.ghostwhisper.service.ContactsHelper
import com.ghostwhisper.service.GhostWhisperService
import com.ghostwhisper.service.KeyDistributor
import com.ghostwhisper.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Channel management screen.
 *
 * Capabilities:
 * - Create/Delete Channels
 * - Activate Channels (for Floating Widget)
 * - Share Keys (WhatsApp/SMS/Copy)
 * - Link to WhatsApp Groups
 * - Manage Members
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repository = remember {
                KeyringRepository(KeyringDatabase.getInstance(context).keyringDao())
        }
        val keyDistributor = remember { KeyDistributor(context) }
        val contactsHelper = remember {
                com.ghostwhisper.service.ContactsHelper(context.contentResolver)
        }

        val channels by repository.activeChannels.collectAsState(initial = emptyList())
        var showCreateDialog by remember { mutableStateOf(false) }
        var showShareDialog by remember { mutableStateOf<ChannelKey?>(null) }
        var showDeleteDialog by remember { mutableStateOf<ChannelKey?>(null) }
        var showSettingsDialog by remember { mutableStateOf<ChannelKey?>(null) }

        Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
                // Header
                Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp, 32.dp, 24.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = "🔑",
                                fontSize = 28.sp,
                                modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "My Channels",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                )
                                Text(
                                        text =
                                                "${channels.size} active channel${if (channels.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                )
                        }
                }

                // Channel List
                if (channels.isEmpty()) {
                        EmptyChannelState()
                } else {
                        LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding =
                                        PaddingValues(bottom = 80.dp, start = 24.dp, end = 24.dp)
                        ) {
                                items(channels, key = { it.keyId }) { channel ->
                                        ChannelCard(
                                                channel = channel,
                                                isActive =
                                                        GhostWhisperService.activeChannelKeyId ==
                                                                channel.keyId,
                                                onActivate = {
                                                        GhostWhisperService.activeChannelKeyId =
                                                                channel.keyId
                                                        GhostWhisperService.activeChannelName =
                                                                channel.channelName
                                                        Toast.makeText(
                                                                        context,
                                                                        "Channel Active: ${channel.channelName}",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                },
                                                onShare = { showShareDialog = channel },
                                                onSettings = { showSettingsDialog = channel },
                                                onDelete = { showDeleteDialog = channel }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                }
                        }
                }
        }

        // FAB
        Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.BottomEnd
        ) {
                ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = GhostPurple,
                        contentColor = DarkBackground,
                        shape = RoundedCornerShape(16.dp)
                ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Channel", fontWeight = FontWeight.SemiBold)
                }
        }

        // ─── Dialogs ─────────────────────────────────────────────────

        if (showCreateDialog) {
                CreateChannelDialog(
                        repository = repository,
                        onDismiss = { showCreateDialog = false },
                        onCreate = { name, linkedGroup ->
                                scope.launch {
                                        repository.createChannel(
                                                name,
                                                linkedGroupName = linkedGroup
                                        )
                                        showCreateDialog = false
                                }
                        }
                )
        }

        showShareDialog?.let { channel ->
                ShareChannelDialog(
                        channel = channel,
                        repository = repository,
                        keyDistributor = keyDistributor,
                        onDismiss = { showShareDialog = null }
                )
        }

        showSettingsDialog?.let { channel ->
                ChannelSettingsDialog(
                        channel = channel,
                        repository = repository,
                        onDismiss = { showSettingsDialog = null }
                )
        }

        showDeleteDialog?.let { channel ->
                DeleteChannelDialog(
                        channel = channel,
                        onDismiss = { showDeleteDialog = null },
                        onConfirm = {
                                scope.launch {
                                        repository.deleteChannel(channel.keyId)
                                        if (GhostWhisperService.activeChannelKeyId == channel.keyId
                                        ) {
                                                GhostWhisperService.activeChannelKeyId = null
                                                GhostWhisperService.activeChannelName = null
                                        }
                                        showDeleteDialog = null
                                }
                        }
                )
        }
}

// ─── Components ──────────────────────────────────────────────────

@Composable
fun EmptyChannelState() {
        Box(
                modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔐", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                "No channels yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                        )
                        Text(
                                "Create a channel to start sending\nencrypted messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                        )
                }
        }
}

@Composable
fun ChannelCard(
        channel: ChannelKey,
        isActive: Boolean,
        onActivate: () -> Unit,
        onShare: () -> Unit,
        onSettings: () -> Unit,
        onDelete: () -> Unit
) {
        val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

        Card(
                modifier = Modifier.fillMaxWidth().clickable { onActivate() },
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isActive) GhostPurple.copy(alpha = 0.15f)
                                        else DarkSurface
                        ),
                border = if (isActive) BorderStroke(1.dp, GhostPurple.copy(alpha = 0.5f)) else null
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                // Icon
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                if (isActive) GhostPurple
                                                                else
                                                                        GhostMediumGray.copy(
                                                                                alpha = 0.3f
                                                                        ),
                                                                CircleShape
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                if (isActive) "🔒" else "#",
                                                fontSize = 20.sp,
                                                color = if (isActive) Color.White else TextMuted
                                        )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Info
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = channel.channelName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                text =
                                                        "Created ${dateFormat.format(Date(channel.createdAt))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                        )
                                        if (channel.linkedGroupName != null) {
                                                Text(
                                                        text =
                                                                "\uD83D\uDD17 ${channel.linkedGroupName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GhostGreen
                                                )
                                        }
                                }

                                // Actions
                                IconButton(onClick = onShare) {
                                        Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = GhostPurple
                                        )
                                }
                                IconButton(onClick = onSettings) {
                                        Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = TextSecondary
                                        )
                                }
                                IconButton(onClick = onDelete) {
                                        Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = GhostRed
                                        )
                                }
                        }
                }
        }
}

// ─── Dialogs Implementation ──────────────────────────────────────

@Composable
fun CreateChannelDialog(
        repository: KeyringRepository,
        onDismiss: () -> Unit,
        onCreate: (name: String, linkedGroup: String?) -> Unit
) {
        var name by remember { mutableStateOf("") }
        var linkedGroup by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkSurface,
                title = { Text("New Channel") },
                text = {
                        Column {
                                // Channel Name
                                OutlinedTextField(
                                        value = name,
                                        onValueChange = {
                                                name = it
                                                errorMessage = null
                                        },
                                        label = { Text("Channel Name") },
                                        singleLine = true,
                                        isError = errorMessage != null,
                                        supportingText =
                                                errorMessage?.let {
                                                        {
                                                                Text(
                                                                        it,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                        }
                                                },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple
                                                )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Linked WhatsApp Group
                                OutlinedTextField(
                                        value = linkedGroup,
                                        onValueChange = { linkedGroup = it },
                                        label = { Text("Linked WhatsApp Group (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple
                                                )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Scan from WhatsApp button
                                OutlinedButton(
                                        onClick = {
                                                val detected =
                                                        GhostWhisperService.lastDetectedGroupName
                                                if (detected != null) {
                                                        linkedGroup = detected
                                                        if (name.isBlank()) name = detected
                                                } else {
                                                        errorMessage =
                                                                "Open a WhatsApp group first, then come back and tap this."
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, GhostPurple)
                                ) {
                                        Icon(
                                                Icons.Default.QrCodeScanner,
                                                contentDescription = null,
                                                tint = GhostPurple
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scan from WhatsApp", color = GhostPurple)
                                }

                                Text(
                                        "Auto-fills the group name from your last opened WhatsApp chat.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 4.dp)
                                )
                        }
                },
                confirmButton = {
                        Button(
                                onClick = {
                                        if (name.isBlank()) return@Button
                                        scope.launch {
                                                if (repository.channelNameExists(name.trim())) {
                                                        errorMessage =
                                                                "A channel with this name already exists."
                                                } else {
                                                        val group =
                                                                linkedGroup.trim().ifBlank { null }
                                                        onCreate(name.trim(), group)
                                                }
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GhostPurple)
                        ) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
}

@Composable
fun ShareChannelDialog(
        channel: ChannelKey,
        repository: KeyringRepository,
        keyDistributor: KeyDistributor,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val activity = context as? Activity
        val shareUri = repository.generateShareUri(channel)

        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkSurface,
                title = { Text("Share \"${channel.channelName}\"") },
                text = {
                        Column {
                                Text(
                                        "Share the key to allow others to read/write in this channel.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                        onClick = {
                                                activity?.let {
                                                        keyDistributor.shareViaWhatsApp(it, channel)
                                                }
                                                onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor = GhostGreen
                                                )
                                ) { Text("Share via WhatsApp") }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                        onClick = {
                                                val clipboard =
                                                        context.getSystemService(
                                                                Context.CLIPBOARD_SERVICE
                                                        ) as
                                                                android.content.ClipboardManager
                                                val clip =
                                                        android.content.ClipData.newPlainText(
                                                                "Ghost Key",
                                                                shareUri
                                                        )
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(
                                                                context,
                                                                "Link copied!",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                ) { Text("Copy Link") }
                        }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
}

@Composable
fun ChannelSettingsDialog(
        channel: ChannelKey,
        repository: KeyringRepository,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val activity = context as? Activity
        val scope = rememberCoroutineScope()
        val keyDistributor = remember { KeyDistributor(context) }
        val contactsHelper = remember {
                com.ghostwhisper.service.ContactsHelper(context.contentResolver)
        }

        var linkedGroup by remember { mutableStateOf(channel.linkedGroupName ?: "") }

        // Editable channel name
        var channelName by remember { mutableStateOf(channel.channelName) }
        var nameEdited by remember { mutableStateOf(false) }

        // Add member form
        var newMemberName by remember { mutableStateOf("") }
        var newMemberPhone by remember { mutableStateOf("") }
        var showAddMember by remember { mutableStateOf(false) }

        // Share Key Prompt State
        var showSharePrompt by remember { mutableStateOf<String?>(null) } // Name of added member

        val contactLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                        contract =
                                androidx.activity.result.contract.ActivityResultContracts
                                        .PickContact()
                ) { uri: android.net.Uri? ->
                        uri?.let {
                                val contact = contactsHelper.getContactFromUri(it)
                                if (contact != null) {
                                        newMemberName = contact.displayName
                                        newMemberPhone = contact.phoneNumber
                                }
                        }
                }

        // Members
        val members by repository.getMembers(channel.keyId).collectAsState(initial = emptyList())

        if (showSharePrompt != null) {
                AlertDialog(
                        onDismissRequest = { showSharePrompt = null },
                        title = { Text("Share Key?") },
                        text = {
                                Text(
                                        "Do you want to send the channel key to ${showSharePrompt} via WhatsApp now?"
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                activity?.let { act ->
                                                        keyDistributor.shareViaWhatsApp(
                                                                act,
                                                                channel
                                                        )
                                                }
                                                showSharePrompt = null
                                        }
                                ) { Text("Yes, Share") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showSharePrompt = null }) { Text("Later") }
                        }
                )
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkSurface,
                title = { Text("Settings: ${channel.channelName}") },
                text = {
                        Column(
                                modifier =
                                        Modifier.fillMaxHeight(0.7f)
                                                .verticalScroll(rememberScrollState())
                        ) {
                                // ─── Rename Channel ──────────────────────
                                Text(
                                        "Channel Name",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GhostPurple
                                )
                                OutlinedTextField(
                                        value = channelName,
                                        onValueChange = {
                                                channelName = it
                                                nameEdited = it != channel.channelName
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple
                                                ),
                                        trailingIcon = {
                                                if (nameEdited) {
                                                        IconButton(
                                                                onClick = {
                                                                        if (channelName.isNotBlank()
                                                                        ) {
                                                                                scope.launch {
                                                                                        try {
                                                                                                repository
                                                                                                        .renameChannel(
                                                                                                                channel.keyId,
                                                                                                                channelName
                                                                                                                        .trim()
                                                                                                        )
                                                                                                nameEdited =
                                                                                                        false
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "Renamed!",
                                                                                                                Toast.LENGTH_SHORT
                                                                                                        )
                                                                                                        .show()
                                                                                        } catch (
                                                                                                e:
                                                                                                        Exception) {
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "Rename failed: ${e.message}",
                                                                                                                Toast.LENGTH_SHORT
                                                                                                        )
                                                                                                        .show()
                                                                                        }
                                                                                }
                                                                        }
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Save,
                                                                        contentDescription =
                                                                                "Save Name",
                                                                        tint = GhostGreen
                                                                )
                                                        }
                                                }
                                        }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // ─── Link Group Section ──────────────────
                                Text(
                                        "Linked WhatsApp Group",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GhostPurple
                                )
                                OutlinedTextField(
                                        value = linkedGroup,
                                        onValueChange = { linkedGroup = it },
                                        placeholder = { Text("Exact group name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GhostPurple,
                                                        focusedLabelColor = GhostPurple
                                                ),
                                        trailingIcon = {
                                                IconButton(
                                                        onClick = {
                                                                scope.launch {
                                                                        repository.linkToGroup(
                                                                                channel.keyId,
                                                                                if (linkedGroup
                                                                                                .isBlank()
                                                                                )
                                                                                        null
                                                                                else linkedGroup
                                                                        )
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        if (linkedGroup
                                                                                                        .isBlank()
                                                                                        )
                                                                                                "Group unlinked"
                                                                                        else
                                                                                                "Group linked!",
                                                                                        Toast.LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                }
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Save,
                                                                contentDescription = "Save"
                                                        )
                                                }
                                        }
                                )
                                Text(
                                        "Auto-switches channel when you enter this group.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = GhostMediumGray)
                                Spacer(modifier = Modifier.height(16.dp))

                                // ─── Members Section ─────────────────────
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                "Members (${members.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = GhostPurple
                                        )
                                        IconButton(
                                                onClick = { showAddMember = !showAddMember },
                                                modifier = Modifier.size(28.dp)
                                        ) {
                                                Icon(
                                                        if (showAddMember) Icons.Default.Close
                                                        else Icons.Default.PersonAdd,
                                                        contentDescription =
                                                                if (showAddMember) "Cancel"
                                                                else "Add Member",
                                                        tint = GhostGreen,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        }
                                }

                                // Add Member Form
                                if (showAddMember) {
                                        Column(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 8.dp)
                                                                .background(
                                                                        DarkBackground.copy(
                                                                                alpha = 0.5f
                                                                        ),
                                                                        RoundedCornerShape(8.dp)
                                                                )
                                                                .padding(12.dp)
                                        ) {
                                                OutlinedTextField(
                                                        value = newMemberName,
                                                        onValueChange = { newMemberName = it },
                                                        placeholder = { Text("Contact name") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true,
                                                        colors =
                                                                OutlinedTextFieldDefaults.colors(
                                                                        focusedBorderColor =
                                                                                GhostGreen,
                                                                        focusedLabelColor =
                                                                                GhostGreen
                                                                )
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        OutlinedTextField(
                                                                value = newMemberPhone,
                                                                onValueChange = {
                                                                        newMemberPhone = it
                                                                },
                                                                placeholder = {
                                                                        Text("+91 98765 43210")
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                singleLine = true,
                                                                keyboardOptions =
                                                                        KeyboardOptions(
                                                                                keyboardType =
                                                                                        KeyboardType
                                                                                                .Phone
                                                                        ),
                                                                colors =
                                                                        OutlinedTextFieldDefaults
                                                                                .colors(
                                                                                        focusedBorderColor =
                                                                                                GhostGreen,
                                                                                        focusedLabelColor =
                                                                                                GhostGreen
                                                                                )
                                                        )
                                                        IconButton(
                                                                onClick = {
                                                                        contactLauncher.launch(null)
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Contacts,
                                                                        contentDescription =
                                                                                "Pick Contact",
                                                                        tint = GhostPurple
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                        onClick = {
                                                                val phone = newMemberPhone.trim()
                                                                // Validate phone: must be 10+
                                                                // digits, optionally with + prefix
                                                                val phoneRegex =
                                                                        Regex("^\\+?[0-9]{10,15}$")
                                                                if (!phoneRegex.matches(phone)) {
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        "Enter a valid phone number (10+ digits)",
                                                                                        Toast.LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                } else {
                                                                        scope.launch {
                                                                                try {
                                                                                        repository
                                                                                                .addMember(
                                                                                                        channelKeyId =
                                                                                                                channel.keyId,
                                                                                                        contactName =
                                                                                                                newMemberName
                                                                                                                        .ifBlank {
                                                                                                                                "Unknown"
                                                                                                                        },
                                                                                                        phoneNumber =
                                                                                                                phone
                                                                                                )

                                                                                        // Prompt to
                                                                                        // share key
                                                                                        showSharePrompt =
                                                                                                newMemberName
                                                                                                        .ifBlank {
                                                                                                                "this user"
                                                                                                        }

                                                                                        newMemberName =
                                                                                                ""
                                                                                        newMemberPhone =
                                                                                                ""
                                                                                        showAddMember =
                                                                                                false
                                                                                        // Toast.makeText(
                                                                                        //
                                                                                        //
                                                                                        // context,
                                                                                        //
                                                                                        //
                                                                                        // "Member
                                                                                        // added!",
                                                                                        //
                                                                                        //
                                                                                        // Toast.LENGTH_SHORT
                                                                                        //         )
                                                                                        //
                                                                                        // .show()
                                                                                } catch (
                                                                                        e:
                                                                                                Exception) {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Failed to add: ${e.message}",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        }
                                                                }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor = GhostPurple
                                                                )
                                                ) { Text("Add & Share") }
                                        }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Member List
                                members.forEach { member ->
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                member.contactName,
                                                                color = TextPrimary
                                                        )
                                                        Text(
                                                                member.phoneNumber,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color = TextMuted
                                                        )
                                                }
                                                // Status Icon
                                                val statusIcon =
                                                        when (member.keyDeliveryStatus) {
                                                                KeyDeliveryStatus.DELIVERED -> "✅"
                                                                KeyDeliveryStatus.PENDING -> "⏳"
                                                                KeyDeliveryStatus.FAILED -> "❌"
                                                                else -> "?"
                                                        }
                                                Text(
                                                        statusIcon,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                )
                                                // Delete button
                                                IconButton(
                                                        onClick = {
                                                                scope.launch {
                                                                        try {
                                                                                repository
                                                                                        .removeMember(
                                                                                                member.id
                                                                                        )
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "${member.contactName} removed",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        } catch (e: Exception) {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Failed to remove: ${e.message}",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription =
                                                                        "Remove Member",
                                                                tint = GhostRed,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                }
                                        }
                                }

                                if (members.isEmpty()) {
                                        Text(
                                                "No members yet. Tap + to add.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                }
                        }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )
}

@Composable
fun DeleteChannelDialog(channel: ChannelKey, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkSurface,
                title = { Text("Delete Channel?") },
                text = { Text("This cannot be undone.", color = TextSecondary) },
                confirmButton = {
                        Button(
                                onClick = onConfirm,
                                colors = ButtonDefaults.buttonColors(containerColor = GhostRed)
                        ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
}
