package com.ghostwhisper.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.ghostwhisper.crypto.AESCrypto
import com.ghostwhisper.data.db.KeyringDatabase
import com.ghostwhisper.data.model.GhostPacket
import com.ghostwhisper.data.repository.KeyringRepository
import com.ghostwhisper.stegano.SteganoCodec
import kotlinx.coroutines.*

/**
 * Core Accessibility Service for Ghost Whisper.
 *
 * Responsibilities:
 * 1. Detect when WhatsApp is in the foreground → signal widget visibility
 * 2. Detect current Chat Title → auto-switch active channel
 * 3. Scan incoming message bubbles for Zero-Width characters
 * 4. Execute the Silent Fail decryption protocol
 * 5. Inject encrypted payloads into the message input field
 * 6. Guard clipboard by stripping ZW characters on copy events
 *
 * Security: This service ONLY processes events from com.whatsapp (enforced by
 * accessibility_service_config.xml)
 */
class GhostWhisperService : AccessibilityService() {

    companion object {
        private const val TAG = "GhostWhisperService"
        const val WHATSAPP_PACKAGE = "com.whatsapp"

        // Known WhatsApp View IDs (Subject to change with WA updates)
        const val ID_INPUT_FIELD = "com.whatsapp:id/entry"
        const val ID_SEND_BUTTON = "com.whatsapp:id/send"
        const val ID_CHAT_TITLE = "com.whatsapp:id/conversation_contact_name"
        const val ID_CHAT_TITLE_ALT =
                "com.whatsapp:id/conversation_contact_photo" // Sometimes useful

        // Service state accessible to UI
        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var isWhatsAppActive: Boolean = false
            private set

        // Channel selected by the user OR auto-detected
        @Volatile var activeChannelKeyId: String? = null
        @Volatile var activeChannelName: String? = null

        // Callback for decrypted messages
        var onMessageDecrypted: ((channelName: String, plaintext: String) -> Unit)? = null

        // Callback for WhatsApp state changes
        var onWhatsAppStateChanged: ((isActive: Boolean) -> Unit)? = null

        // Callback for Channel Auto-Switch
        var onChannelAutoSwitched: ((channelName: String) -> Unit)? = null

        /** Last WhatsApp chat/group title detected by the accessibility service. */
        @Volatile var lastDetectedGroupName: String? = null

        @Volatile var instance: GhostWhisperService? = null

        fun startEncryptionFlow() {
            instance?.beginEncryptionFlow()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: KeyringRepository
    private lateinit var contactsHelper: ContactsHelper
    private lateinit var notificationHelper: NotificationHelper

    private var clipboardManager: ClipboardManager? = null
    private var clipboardGuardEnabled = true

    // Cache current chat to avoid repetitive DB lookups
    private var lastChatTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = KeyringDatabase.getInstance(applicationContext)
        repository = KeyringRepository(db.keyringDao())
        contactsHelper = ContactsHelper(contentResolver)
        notificationHelper = NotificationHelper(applicationContext)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        isServiceRunning = true
        Log.d(TAG, "GhostWhisperService created")
    }

    override fun onDestroy() {
        isServiceRunning = false
        isWhatsAppActive = false
        EncryptionOverlayManager.cleanup(applicationContext)
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "GhostWhisperService destroyed")
        super.onDestroy()
    }

    /**
     * Entry point for the encryption UX flow.
     *
     * 1. Read the typed message from WhatsApp input
     * 2. Resolve the channel (auto if single, picker if multiple)
     * 3. Show cover message overlay
     * 4. Encrypt → inject → auto-send
     */
    fun beginEncryptionFlow() {
        serviceScope.launch {
            try {
                val text = getInputText()
                if (text.isNullOrBlank()) {
                    showToast("Type a message first")
                    return@launch
                }

                val allChannels = repository.getAllActiveKeys()
                Log.d(TAG, "Encryption flow: found ${allChannels.size} channel(s)")

                when {
                    allChannels.isEmpty() -> {
                        showToast("Create a channel first")
                    }
                    allChannels.size == 1 -> {
                        val channel = allChannels.first()
                        activeChannelKeyId = channel.keyId
                        activeChannelName = channel.channelName
                        Log.d(TAG, "Auto-selected channel: ${channel.channelName}")
                        withContext(Dispatchers.Main) {
                            onChannelAutoSwitched?.invoke(channel.channelName)
                            showCoverMessageOverlay(channel.channelName, text, channel.keyId)
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            EncryptionOverlayManager.showChannelPicker(
                                    context = applicationContext,
                                    channels = allChannels,
                                    onSelected = { selected ->
                                        activeChannelKeyId = selected.keyId
                                        activeChannelName = selected.channelName
                                        Log.d(TAG, "User selected channel: ${selected.channelName}")
                                        onChannelAutoSwitched?.invoke(selected.channelName)
                                        EncryptionOverlayManager.dismissOverlay(applicationContext)
                                        showCoverMessageOverlay(
                                                selected.channelName,
                                                text,
                                                selected.keyId
                                        )
                                    },
                                    onCancel = {
                                        Log.d(TAG, "User cancelled channel picker")
                                        EncryptionOverlayManager.dismissOverlay(applicationContext)
                                    }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Encryption flow error: ${e.message}", e)
                showToast("Encryption error — please try again")
            }
        }
    }

    private fun showCoverMessageOverlay(
            channelName: String,
            secretText: String,
            channelKeyId: String
    ) {
        EncryptionOverlayManager.showCoverMessageInput(
                context = applicationContext,
                channelName = channelName,
                secretText = secretText,
                onSend = { coverMessage ->
                    Log.d(TAG, "Cover message entered, encrypting for channel: $channelName")
                    EncryptionOverlayManager.dismissOverlay(applicationContext)
                    injectEncryptedMessage(secretText, coverMessage, channelKeyId)
                },
                onCancel = {
                    Log.d(TAG, "User cancelled cover message input")
                    EncryptionOverlayManager.dismissOverlay(applicationContext)
                }
        )
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInputText(): String? {
        val root = rootInActiveWindow ?: return null
        try {
            // Try focused node first
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                val text = focused.text?.toString()
                focused.recycle()
                return text
            }

            // Fallback to ID lookup
            val nodes = root.findAccessibilityNodeInfosByViewId(ID_INPUT_FIELD)
            val node = nodes?.firstOrNull()
            val text = node?.text?.toString()
            node?.recycle()
            return text
        } catch (e: Exception) {
            return null
        } finally {
            root.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "GhostWhisperService connected")

        // Set up clipboard guard
        setupClipboardGuard()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Safety: only process WhatsApp events
        if (event.packageName?.toString() == WHATSAPP_PACKAGE) {
            // Only process content from WhatsApp, but handle window state for all apps to know when
            // to hide widget
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentChanged(event)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            else -> {
                /* Ignore */
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "GhostWhisperService interrupted")
    }

    // ─── Event Handlers ─────────────────────────────────────────────

    /** Detect WhatsApp foreground state & Chat Title. */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val isWhatsApp = event.packageName?.toString() == WHATSAPP_PACKAGE
        if (isWhatsAppActive != isWhatsApp) {
            isWhatsAppActive = isWhatsApp
            onWhatsAppStateChanged?.invoke(isWhatsApp)
            Log.d(TAG, "WhatsApp active: $isWhatsApp")
        }

        if (isWhatsApp) {
            detectAndLinkChat()
        }
    }

    /** Scan new message content for hidden payloads. */
    private fun handleContentChanged(event: AccessibilityEvent) {
        val source = event.source ?: return

        // Periodically check chat title even on content change (e.g. searching)
        detectAndLinkChat()

        serviceScope.launch {
            try {
                scanNodeForPayloads(source)
            } catch (e: Exception) {
                // Silent fail: log only in debug
                Log.d(TAG, "Scan error: ${e.message}")
            } finally {
                source.recycle()
            }
        }
    }

    /** Monitor text input changes (for widget glow state). */
    private fun handleTextChanged(event: AccessibilityEvent) {
        // Implementation for typing detection
    }

    // ─── Chat Linking Logic ─────────────────────────────────────────

    /** Attempt to identify the current chat and link to a channel. */
    private fun detectAndLinkChat() {
        val rootNode = rootInActiveWindow ?: return

        try {
            val titleNode = findChatTitleNode(rootNode)
            val title = titleNode?.text?.toString()

            if (!title.isNullOrEmpty() && title != lastChatTitle) {
                lastChatTitle = title
                lastDetectedGroupName = title
                Log.d(TAG, "Detected chat title: $title")

                checkLinkedGroup(title)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error linking chat: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }

    private fun findChatTitleNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy A: ID lookup
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_CHAT_TITLE)
        if (!nodes.isNullOrEmpty()) return nodes[0]

        // Strategy B: Heuristic (implied for future if ID fails)
        // Look for text in the top Action Bar area
        return null
    }

    private fun checkLinkedGroup(groupName: String) {
        serviceScope.launch {
            val linkedChannels = repository.findChannelsByGroup(groupName)
            if (linkedChannels.isNotEmpty()) {
                val channel = linkedChannels.first() // Pick first if multiple

                // Auto-switch!
                if (activeChannelKeyId != channel.keyId) {
                    activeChannelKeyId = channel.keyId
                    activeChannelName = channel.channelName

                    withContext(Dispatchers.Main) {
                        onChannelAutoSwitched?.invoke(channel.channelName)
                        Log.i(TAG, "Auto-linked to channel: ${channel.channelName}")
                    }
                }
            } else {
                // Determine if we should clear active channel?
                // For now, keep it sticky to avoid annoyance,
                // or clear it if the user explicitly left the linked chat.
                // Let's NOT clear it automatically to allow manual override.
            }
        }
    }

    // ─── Silent Fail Decryption Protocol ────────────────────────────

    /** Recursively scan an accessibility node tree for ZW payloads. */
    private suspend fun scanNodeForPayloads(node: AccessibilityNodeInfo) {
        val text = node.text?.toString()
        if (text != null && SteganoCodec.containsPayload(text)) {
            attemptDecrypt(text)
        }

        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null && SteganoCodec.containsPayload(contentDesc)) {
            attemptDecrypt(contentDesc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    scanNodeForPayloads(child)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private suspend fun attemptDecrypt(rawMessage: String) {
        try {
            val zwPayload = SteganoCodec.extractPayload(rawMessage) ?: return
            val payloadBytes = SteganoCodec.decode(zwPayload)
            val packet = GhostPacket.fromBytes(payloadBytes)

            val channelKey = repository.findByKeyId(packet.keyId) ?: return

            val key = AESCrypto.keyFromBase64(channelKey.aesKeyBase64)
            val plaintext =
                    AESCrypto.decrypt(
                            ciphertext = packet.ciphertextBytes(),
                            key = key,
                            iv = packet.ivBytes()
                    )

            withContext(Dispatchers.Main) {
                onMessageDecrypted?.invoke(channelKey.channelName, plaintext)

                // Also trigger notification if screen is off or similar?
                // For now, just Overlay
                OverlayManager.showOverlay(
                        context = this@GhostWhisperService,
                        channelName = channelKey.channelName,
                        message = plaintext,
                        coverMessage = rawMessage, // Pass original text as cover
                        onReply = {
                            activeChannelKeyId = channelKey.keyId
                            activeChannelName = channelKey.channelName
                        }
                )
            }
        } catch (e: Exception) {
            return // Silent fail
        }
    }

    // ─── Text Injection (Sender Side) ───────────────────────────────

    fun injectEncryptedMessage(plaintext: String, coverMessage: String, channelKeyId: String) {
        serviceScope.launch {
            try {
                // Wait for overlay to dismiss and WhatsApp to regain focus
                delay(300)

                val channelKey = repository.findByKeyId(channelKeyId)
                if (channelKey == null) {
                    Log.e(TAG, "Channel key not found: $channelKeyId")
                    showToast("Channel key not found")
                    return@launch
                }

                val key = AESCrypto.keyFromBase64(channelKey.aesKeyBase64)
                val encrypted = AESCrypto.encrypt(plaintext, key)
                val packet =
                        GhostPacket(
                                keyId = channelKey.keyId,
                                iv = encrypted.ivBase64(),
                                ciphertext = encrypted.ciphertextBase64()
                        )

                val zwPayload = SteganoCodec.encode(packet.toBytes())
                val fullMessage = SteganoCodec.injectPayload(coverMessage, zwPayload)
                Log.d(TAG, "Encrypted payload ready (${fullMessage.length} chars)")

                withContext(Dispatchers.Main) {
                    val sent = injectTextIntoWhatsApp(fullMessage)
                    if (sent) {
                        showToast("Message encrypted & sent ✓")
                    } else {
                        showToast("Could not inject — is WhatsApp focused?")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Encryption/injection failed: ${e.message}", e)
                showToast("Encryption failed — try again")
            }
        }
    }

    /**
     * Injects text into WhatsApp's input field and auto-taps Send. Returns true if injection
     * succeeded, false otherwise.
     */
    private fun injectTextIntoWhatsApp(text: String): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Cannot inject: rootInActiveWindow is null")
            return false
        }
        try {
            val inputNodes = rootNode.findAccessibilityNodeInfosByViewId(ID_INPUT_FIELD)
            val inputNode = inputNodes?.firstOrNull()
            if (inputNode == null) {
                Log.w(TAG, "Cannot inject: WhatsApp input field not found")
                return false
            }
            try {
                val arguments = Bundle()
                arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                )
                val textSet =
                        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (!textSet) {
                    Log.w(TAG, "ACTION_SET_TEXT returned false")
                    return false
                }

                // Small delay so WhatsApp renders the Send button
                Thread.sleep(150)

                // Re-acquire root after delay (WhatsApp may have updated the view tree)
                val freshRoot = rootInActiveWindow ?: rootNode
                val sendNodes = freshRoot.findAccessibilityNodeInfosByViewId(ID_SEND_BUTTON)
                val sendNode = sendNodes?.firstOrNull()
                if (sendNode != null) {
                    sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Encrypted message sent")
                    sendNode.recycle()
                } else {
                    Log.w(TAG, "Send button not found — message is in input, user must tap Send")
                }
                if (freshRoot !== rootNode) {
                    try {
                        freshRoot.recycle()
                    } catch (e: Exception) {
                        Log.w(TAG, "freshRoot.recycle() failed (already recycled?)", e)
                    }
                }
                return true
            } finally {
                inputNode.recycle()
            }
        } finally {
            rootNode.recycle()
        }
    }

    // ─── Clipboard Guard ────────────────────────────────────────────

    private fun setupClipboardGuard() {
        clipboardManager?.addPrimaryClipChangedListener {
            if (!clipboardGuardEnabled) return@addPrimaryClipChangedListener
            try {
                val clip = clipboardManager?.primaryClip ?: return@addPrimaryClipChangedListener
                if (clip.itemCount == 0) return@addPrimaryClipChangedListener

                val text =
                        clip.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener

                if (SteganoCodec.containsPayload(text)) {
                    val cleanText = SteganoCodec.stripZeroWidth(text)
                    val cleanClip = ClipData.newPlainText("text", cleanText)
                    clipboardManager?.setPrimaryClip(cleanClip)
                    Log.d(TAG, "Clipboard guard: stripped ZW characters")
                }
            } catch (e: Exception) {
                /* Ignore */
            }
        }
    }

    fun setClipboardGuardEnabled(enabled: Boolean) {
        clipboardGuardEnabled = enabled
    }
}
