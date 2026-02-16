package com.ghostwhisper.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Manages system notifications for Ghost Whisper.
 *
 * Notification types:
 * - Decrypted message received
 * - Key delivery status updates
 * - Channel dissolution alerts
 */
class NotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_MESSAGES = "ghost_messages"
        private const val CHANNEL_KEYS = "ghost_keys"
        private const val CHANNEL_ALERTS = "ghost_alerts"
    }

    init {
        createNotificationChannels()
    }

    /** Create notification channels (required for Android 8+). */
    private fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messageChannel =
                NotificationChannel(
                                CHANNEL_MESSAGES,
                                "Decrypted Messages",
                                NotificationManager.IMPORTANCE_HIGH
                        )
                        .apply {
                            description = "Notifications for successfully decrypted hidden messages"
                            enableVibration(true)
                        }

        val keyChannel =
                NotificationChannel(
                                CHANNEL_KEYS,
                                "Key Distribution",
                                NotificationManager.IMPORTANCE_DEFAULT
                        )
                        .apply { description = "Updates about channel key delivery status" }

        val alertChannel =
                NotificationChannel(
                                CHANNEL_ALERTS,
                                "Channel Alerts",
                                NotificationManager.IMPORTANCE_HIGH
                        )
                        .apply {
                            description = "Important channel alerts like dissolution"
                            enableVibration(true)
                        }

        manager.createNotificationChannel(messageChannel)
        manager.createNotificationChannel(keyChannel)
        manager.createNotificationChannel(alertChannel)
    }

    /** Show a notification when a hidden message is successfully decrypted. */
    fun notifyMessageDecrypted(channelName: String, preview: String) {
        if (!hasNotificationPermission()) return

        val notification =
                NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setContentTitle("🔓 Hidden message in \"$channelName\"")
                        .setContentText(preview.take(100))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()

        NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Show a notification for key delivery status update. */
    fun notifyKeyDelivered(contactName: String, channelName: String, viaSms: Boolean) {
        if (!hasNotificationPermission()) return

        val method = if (viaSms) "SMS" else "WhatsApp"
        val notification =
                NotificationCompat.Builder(context, CHANNEL_KEYS)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle("🔑 Key sent to $contactName")
                        .setContentText("Channel \"$channelName\" key sent via $method")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()

        NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Show a notification when a channel is dissolved. */
    fun notifyChannelDissolved(channelName: String) {
        if (!hasNotificationPermission()) return

        val notification =
                NotificationCompat.Builder(context, CHANNEL_ALERTS)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("🔒 Channel dissolved")
                        .setContentText("\"$channelName\" has been dissolved by the creator")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()

        NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Check if POST_NOTIFICATIONS permission is granted (Android 13+). */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
