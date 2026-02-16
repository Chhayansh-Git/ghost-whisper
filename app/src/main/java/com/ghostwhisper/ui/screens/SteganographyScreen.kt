package com.ghostwhisper.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.ghostwhisper.crypto.AESCrypto
import com.ghostwhisper.data.db.KeyringDatabase
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.model.GhostPacket
import com.ghostwhisper.data.repository.KeyringRepository
import com.ghostwhisper.service.DCTSteganographyHelper
import com.ghostwhisper.service.SteganographyHelper
import com.ghostwhisper.ui.theme.DarkBackground
import com.ghostwhisper.ui.theme.DarkSurface
import com.ghostwhisper.ui.theme.GhostPurple
import com.ghostwhisper.ui.theme.GhostRed
import com.ghostwhisper.ui.theme.TextMuted
import com.ghostwhisper.ui.theme.TextPrimary
import com.ghostwhisper.ui.theme.TextSecondary
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteganographyScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Encode, 1 = Decode

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Steganography Lab", color = TextPrimary) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )
            },
            containerColor = DarkBackground
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
        ) {
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = GhostPurple,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = GhostPurple
                        )
                    }
            ) {
                Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Encode") },
                        icon = { Icon(Icons.Default.Lock, null) }
                )
                Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Decode") },
                        icon = { Icon(Icons.Default.LockOpen, null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                EncodeSection()
            } else {
                DecodeSection()
            }
        }
    }
}

@Composable
fun EncodeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Channel selection for encryption
    val repository = remember {
        KeyringRepository(KeyringDatabase.getInstance(context).keyringDao())
    }
    var channels by remember { mutableStateOf<List<ChannelKey>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<ChannelKey?>(null) }
    var channelDropdownExpanded by remember { mutableStateOf(false) }

    // Load channels
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { channels = repository.getAllActiveKeys() }
    }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceGifBytes by remember { mutableStateOf<ByteArray?>(null) }

    var encodedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var encodedGifBytes by remember { mutableStateOf<ByteArray?>(null) }

    var message by remember { mutableStateOf("") }
    var isRobustMode by remember {
        mutableStateOf(true)
    } // Default to robust based on user feedback
    var isProcessing by remember { mutableStateOf(false) }

    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    imageUri = it
                    encodedBitmap = null // Reset previous result
                    encodedGifBytes = null
                    sourceBitmap = null
                    sourceGifBytes = null

                    // Load content
                    try {
                        val type = context.contentResolver.getType(it)
                        if (type?.contains("gif") == true) {
                            // Handle GIF
                            val bytes =
                                    context.contentResolver.openInputStream(it)?.use { stream ->
                                        stream.readBytes()
                                    }
                            sourceGifBytes = bytes
                        } else {
                            // Handle Static Image
                            val inputStream = context.contentResolver.openInputStream(it)
                            sourceBitmap = BitmapFactory.decodeStream(inputStream)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(200.dp)
                                .background(DarkSurface, RoundedCornerShape(12.dp))
                                .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
        ) {
            if (encodedGifBytes != null) {
                GifImage(gifBytes = encodedGifBytes!!, modifier = Modifier.fillMaxSize())
            } else if (encodedBitmap != null) {
                Image(
                        bitmap = encodedBitmap!!.asImageBitmap(),
                        contentDescription = "Encoded Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                )
            } else if (sourceGifBytes != null) {
                GifImage(gifBytes = sourceGifBytes!!, modifier = Modifier.fillMaxSize())
            } else if (sourceBitmap != null) {
                Image(
                        bitmap = sourceBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                    )
                    Text("Tap to select image", color = TextMuted)
                }
            }
        }

        if (sourceGifBytes != null) {
            Text(
                    "Animated GIF detected. Messages will be embedded without losing animation.",
                    color = GhostPurple,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Secret Message") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GhostPurple,
                                focusedLabelColor = GhostPurple,
                                cursorColor = GhostPurple
                        ),
                minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Channel Selector (Required for Encryption) ────────────
        Text(
                "Encrypt with Channel:",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                    onClick = { channelDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GhostPurple)
            ) {
                Text(
                        text = selectedChannel?.channelName ?: "Select a Channel 🔑",
                        color = if (selectedChannel != null) TextPrimary else TextMuted
                )
            }
            DropdownMenu(
                    expanded = channelDropdownExpanded,
                    onDismissRequest = { channelDropdownExpanded = false }
            ) {
                if (channels.isEmpty()) {
                    DropdownMenuItem(
                            text = { Text("No channels. Create one first.") },
                            onClick = { channelDropdownExpanded = false }
                    )
                } else {
                    channels.forEach { channel ->
                        DropdownMenuItem(
                                text = { Text("🔒 ${channel.channelName}") },
                                onClick = {
                                    selectedChannel = channel
                                    channelDropdownExpanded = false
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (sourceGifBytes == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode:", color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                        selected = !isRobustMode,
                        onClick = { isRobustMode = false },
                        label = { Text("High Capacity") },
                        leadingIcon = {
                            if (!isRobustMode) Icon(Icons.Default.Check, null) else null
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                        selected = isRobustMode,
                        onClick = { isRobustMode = true },
                        label = { Text("Robust (DCT)") },
                        leadingIcon = {
                            if (isRobustMode) Icon(Icons.Default.Check, null) else null
                        }
                )
            }

            if (isRobustMode) {
                Text(
                        "Robust mode survives compression (e.g., WhatsApp images) but has lower capacity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
                onClick = {
                    if (sourceBitmap == null && sourceGifBytes == null) {
                        Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (message.isBlank()) {
                        Toast.makeText(context, "Enter a message", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedChannel == null) {
                        Toast.makeText(
                                        context,
                                        "Select a channel for encryption",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                        return@Button
                    }

                    scope.launch {
                        isProcessing = true

                        try {
                            // Step 1: Encrypt the message with the channel's AES key
                            val channel = selectedChannel!!
                            val key = AESCrypto.keyFromBase64(channel.aesKeyBase64)
                            val encrypted = AESCrypto.encrypt(message, key)
                            val packet =
                                    GhostPacket(
                                            keyId = channel.keyId,
                                            iv = encrypted.ivBase64(),
                                            ciphertext = encrypted.ciphertextBase64()
                                    )
                            val packetJson = packet.toJson()

                            if (sourceGifBytes != null) {
                                // GIF Encoding — embed encrypted packet
                                val result =
                                        withContext(Dispatchers.Default) {
                                            SteganographyHelper.encode(sourceGifBytes!!, packetJson)
                                        }
                                encodedGifBytes = result
                                Toast.makeText(
                                                context,
                                                "GIF Encoded + Encrypted! 🔒",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                // Bitmap Encoding — embed encrypted packet
                                val result =
                                        withContext(Dispatchers.Default) {
                                            if (isRobustMode) {
                                                DCTSteganographyHelper.encode(
                                                        sourceBitmap!!,
                                                        packetJson
                                                )
                                            } else {
                                                SteganographyHelper.encode(
                                                        sourceBitmap!!,
                                                        packetJson
                                                )
                                            }
                                        }

                                if (result != null) {
                                    encodedBitmap = result
                                    Toast.makeText(
                                                    context,
                                                    "Encoded + Encrypted! 🔒",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                } else {
                                    val msg =
                                            if (isRobustMode) "Message too long (DCT capacity low)"
                                            else "Message too long"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: OutOfMemoryError) {
                            Toast.makeText(context, "Image too large (OOM)", Toast.LENGTH_LONG)
                                    .show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                            context,
                                            "Encoding failed: ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                            e.printStackTrace()
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GhostPurple),
                enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
            } else {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Encode & Encrypt")
            }
        }

        if (encodedBitmap != null || encodedGifBytes != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                    onClick = {
                        val uri =
                                if (encodedGifBytes != null) {
                                    saveFileToCache(context, encodedGifBytes!!, "stego_image.gif")
                                } else {
                                    saveFileToCache(
                                            context,
                                            bitmapToBytes(encodedBitmap!!),
                                            "stego_image.png"
                                    )
                                }

                        if (uri != null) {
                            shareImage(
                                    context,
                                    uri,
                                    if (encodedGifBytes != null) "image/gif" else "image/png"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TextSecondary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Image (Send as Document)")
            }
            Text(
                    "Important: Send as 'Document' in WhatsApp to preserve the hidden message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostRed,
                    modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun DecodeSection() {
    val context = LocalContext.current
    val repository = remember {
        KeyringRepository(KeyringDatabase.getInstance(context).keyringDao())
    }
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceGifBytes by remember { mutableStateOf<ByteArray?>(null) }

    var decodedMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    sourceBitmap = null
                    sourceGifBytes = null
                    decodedMessage = null
                    statusMessage = ""

                    try {
                        val type = context.contentResolver.getType(it)
                        if (type?.contains("gif") == true) {
                            val bytes =
                                    context.contentResolver.openInputStream(it)?.use { stream ->
                                        stream.readBytes()
                                    }
                            sourceGifBytes = bytes
                        } else {
                            val inputStream = context.contentResolver.openInputStream(it)
                            sourceBitmap = BitmapFactory.decodeStream(inputStream)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(200.dp)
                                .background(DarkSurface, RoundedCornerShape(12.dp))
                                .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
        ) {
            if (sourceGifBytes != null) {
                GifImage(gifBytes = sourceGifBytes!!, modifier = Modifier.fillMaxSize())
            } else if (sourceBitmap != null) {
                Image(
                        bitmap = sourceBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                    )
                    Text("Tap to select image to decode", color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
                onClick = {
                    if (sourceBitmap == null && sourceGifBytes == null) {
                        Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch {
                        isProcessing = true
                        statusMessage = "Analyzing image..."
                        decodedMessage = null

                        try {
                            var rawPayload: String? = null

                            if (sourceGifBytes != null) {
                                // GIF Decode
                                rawPayload =
                                        withContext(Dispatchers.Default) {
                                            SteganographyHelper.decode(sourceGifBytes!!)
                                        }
                            } else {
                                // Bitmap Decode — try LSB first, then DCT
                                rawPayload =
                                        withContext(Dispatchers.Default) {
                                            SteganographyHelper.decode(sourceBitmap!!)
                                        }
                                if (rawPayload == null) {
                                    rawPayload =
                                            withContext(Dispatchers.Default) {
                                                DCTSteganographyHelper.decode(sourceBitmap!!)
                                            }
                                }
                            }

                            if (rawPayload == null) {
                                statusMessage = "No hidden message found."
                            } else {
                                // Try to decrypt as GhostPacket (encrypted)
                                try {
                                    val packet = GhostPacket.fromJson(rawPayload)
                                    val channelKey =
                                            withContext(Dispatchers.IO) {
                                                repository.findByKeyId(packet.keyId)
                                            }

                                    if (channelKey != null) {
                                        val key = AESCrypto.keyFromBase64(channelKey.aesKeyBase64)
                                        val plaintext =
                                                AESCrypto.decrypt(
                                                        ciphertext = packet.ciphertextBytes(),
                                                        key = key,
                                                        iv = packet.ivBytes()
                                                )
                                        decodedMessage = plaintext
                                        statusMessage =
                                                "Message decrypted! 🔓 (Channel: ${channelKey.channelName})"
                                    } else {
                                        // Silent fail: don't reveal that a secret exists
                                        statusMessage = "No hidden message found."
                                    }
                                } catch (e: Exception) {
                                    Log.d(
                                            "SteganographyScreen",
                                            "Not a GhostPacket, showing raw",
                                            e
                                    )
                                    decodedMessage = rawPayload
                                    statusMessage = "Message found! 🔓 (Unencrypted/Legacy)"
                                }
                            }
                        } catch (e: OutOfMemoryError) {
                            statusMessage = "Error: Image too large to process."
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GhostPurple),
                enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
            } else {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Decode Message")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = statusMessage,
                    color = if (decodedMessage != null) GhostPurple else GhostRed,
                    fontWeight = FontWeight.Bold
            )
        }

        if (decodedMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            "Hidden Message:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            decodedMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun GifImage(gifBytes: ByteArray, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                },
                update = { imageView ->
                    try {
                        val source = ImageDecoder.createSource(ByteBuffer.wrap(gifBytes))
                        val drawable = ImageDecoder.decodeDrawable(source)
                        imageView.setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = modifier
        )
    } else {
        val bitmap = BitmapFactory.decodeByteArray(gifBytes, 0, gifBytes.size)
        if (bitmap != null) {
            Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "GIF Preview",
                    modifier = modifier,
                    contentScale = ContentScale.Fit
            )
        }
    }
}

private fun saveFileToCache(context: Context, bytes: ByteArray, filename: String): Uri? {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = FileOutputStream(File(cachePath, filename))
        stream.write(bytes)
        stream.close()

        return androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                File(cachePath, filename)
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

private fun shareImage(context: Context, uri: Uri, mimeType: String) {
    val intent =
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Encrypted Image"))
}
