package com.ghostwhisper.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.ghostwhisper.data.model.ChannelMember
import com.ghostwhisper.data.model.KeyDeliveryStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Handles automatic key distribution to channel members.
 *
 * Primary method: WhatsApp (QR code + deep link) Toggle option: SMS (deep link text only)
 */
class KeyDistributor(private val context: Context) {

    companion object {
        private const val TAG = "KeyDistributor"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val DOWNLOAD_LINK = "https://github.com/user/ghost-whisper/releases"
    }

    /** Result of a distribution attempt. */
    data class DistributionResult(
            val member: ChannelMember,
            val status: KeyDeliveryStatus,
            val error: String? = null
    )

    /**
     * Distribute channel key to a member via WhatsApp (primary).
     *
     * Sends: QR code image + deep link + download link.
     */
    fun distributeViaWhatsApp(
            member: ChannelMember,
            channelName: String,
            keyBase64: String
    ): DistributionResult {
        return try {
            val joinUri = buildJoinUri(channelName, keyBase64)
            val message = buildMessage(channelName, joinUri)

            // Generate QR code
            val qrFile = generateQrCode(joinUri, channelName)

            // Send via WhatsApp to specific contact
            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        setPackage(WHATSAPP_PACKAGE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        qrFile
                                )
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        // Target specific phone number
                        val phoneNumber = member.phoneNumber.replace("+", "")
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
                        // Override type after setting data
                        type = "image/*"
                    }

            context.startActivity(intent)

            DistributionResult(member, KeyDeliveryStatus.SENT_WHATSAPP)
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp distribution failed for ${member.contactName}: ${e.message}")
            DistributionResult(member, KeyDeliveryStatus.PENDING, e.message)
        }
    }

    /**
     * Distribute channel key via SMS (toggle option).
     *
     * Sends: Deep link + download link (no QR — text only).
     */
    fun distributeViaSms(
            member: ChannelMember,
            channelName: String,
            keyBase64: String
    ): DistributionResult {
        return try {
            val joinUri = buildJoinUri(channelName, keyBase64)
            val message = buildMessage(channelName, joinUri)

            @Suppress("DEPRECATION") val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(member.phoneNumber, null, parts, null, null)

            Log.d(TAG, "SMS sent to ${member.contactName} (${member.phoneNumber})")
            DistributionResult(member, KeyDeliveryStatus.SENT_SMS)
        } catch (e: Exception) {
            Log.e(TAG, "SMS distribution failed for ${member.contactName}: ${e.message}")
            DistributionResult(member, KeyDeliveryStatus.PENDING, e.message)
        }
    }

    /**
     * Distribute to all members in a channel.
     *
     * @param useSms If true, also sends via SMS (toggle)
     */
    fun distributeToAll(
            members: List<ChannelMember>,
            channelName: String,
            keyBase64: String,
            useSms: Boolean = false
    ): List<DistributionResult> {
        val results = mutableListOf<DistributionResult>()

        for (member in members.filter { !it.hasKey }) {
            // Primary: WhatsApp
            val whatsappResult = distributeViaWhatsApp(member, channelName, keyBase64)
            results.add(whatsappResult)

            // Toggle: also send via SMS
            if (useSms) {
                val smsResult = distributeViaSms(member, channelName, keyBase64)
                results.add(smsResult)
            }
        }

        return results
    }

    /** Share channel key via WhatsApp to a user-selected contact. */
    fun shareViaWhatsApp(
            channelName: String,
            keyBase64: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
    ) {
        try {
            val joinUri = buildJoinUri(channelName, keyBase64)
            val message = buildMessage(channelName, joinUri)
            val qrFile = generateQrCode(joinUri, channelName)

            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        setPackage(WHATSAPP_PACKAGE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        qrFile
                                )
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            context.startActivity(intent)
            onSuccess()
        } catch (e: Exception) {
            onFailure(e.message ?: "Unknown error")
        }
    }

    /** Share channel key via WhatsApp (Convenience method). */
    fun shareViaWhatsApp(context: Context, channel: com.ghostwhisper.data.model.ChannelKey) {
        shareViaWhatsApp(
                channelName = channel.channelName,
                keyBase64 = channel.aesKeyBase64,
                onSuccess = {},
                onFailure = { Log.e(TAG, "Share failed: $it") }
        )
    }

    // ─── Message building ────────────────────────────────────────

    /** Build the ghostwhisper:// join URI. */
    private fun buildJoinUri(channelName: String, keyBase64: String): String {
        return "https://chhayansh-git.github.io/ghost-whisper/join/?key=${Uri.encode(keyBase64)}&name=${Uri.encode(channelName)}"
    }

    /** Build the distribution message with deep link and download link. */
    private fun buildMessage(channelName: String, joinUri: String): String {
        return """
            |🔐 Ghost Whisper channel invite: "$channelName"
            |
            |📲 Have Ghost Whisper? Tap to join:
            |$joinUri
            |
            |📥 Don't have it yet? Download:
            |$DOWNLOAD_LINK
            |
            |🔒 End-to-end encrypted • Keys never leave your device
        """.trimMargin()
    }

    // ─── QR Code generation ──────────────────────────────────────

    /** Generate a QR code bitmap and save to a temp file. */
    private fun generateQrCode(content: String, channelName: String): File {
        val size = 512
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )
            }
        }

        // Save to cache directory
        val fileName = "ghostwhisper_qr_${channelName.replace(" ", "_")}.png"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        return file
    }

    /** Build a dissolution notification message. */
    fun buildDissolutionMessage(channelName: String): String {
        return "🔒 Ghost Whisper channel \"$channelName\" has been dissolved by the creator. " +
                "Messages can no longer be sent or decrypted on this channel."
    }
}
