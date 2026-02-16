package com.ghostwhisper.ui.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ghostwhisper.service.GhostWhisperService
import com.ghostwhisper.ui.MainActivity

/**
 * Foreground service that renders the floating Ghost icon (👻).
 *
 * Widget States:
 * - IDLE: Semi-transparent ghost on right edge (WhatsApp active, no channel selected)
 * - ACTIVE: Green glow (user is typing in WhatsApp)
 * - CHANNEL: Purple with lock icon (private channel selected)
 * - HIDDEN: Not visible (WhatsApp is not foreground)
 */
class FloatingWidgetService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ghost_whisper_widget"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            isRunning = true
            val intent = Intent(context, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            isRunning = false
            context.stopService(Intent(context, FloatingWidgetService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var widgetView: View? = null
    private var widgetParams: WindowManager.LayoutParams? = null

    // Widget state
    enum class WidgetState {
        IDLE,
        ACTIVE,
        CHANNEL,
        HIDDEN
    }
    private var currentState = WidgetState.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createWidget()

        // Listen for WhatsApp state changes
        GhostWhisperService.onWhatsAppStateChanged = { isActive ->
            if (isActive) {
                updateState(
                        if (GhostWhisperService.activeChannelKeyId != null) WidgetState.CHANNEL
                        else WidgetState.IDLE
                )
            } else {
                updateState(WidgetState.HIDDEN)
            }
        }
    }

    override fun onDestroy() {
        removeWidget()
        GhostWhisperService.onWhatsAppStateChanged = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // ─── Widget Creation ─────────────────────────────────────────────

    private fun createWidget() {
        val dp = { value: Int -> dpToPx(value) }

        widgetView = buildWidgetView()

        widgetParams =
                WindowManager.LayoutParams(
                                dp(56),
                                dp(56),
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.END
                            x = dp(8)
                            y = dp(300)
                        }

        try {
            windowManager?.addView(widgetView, widgetParams)
            setupTouchListener()
        } catch (e: Exception) {
            // Overlay permission might not be granted
        }
    }

    private fun buildWidgetView(): View {
        val dp = { value: Int -> dpToPx(value) }

        return TextView(this).apply {
            text = "👻"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setBackgroundColor(0x00000000) // Transparent background
            setPadding(dp(8), dp(8), dp(8), dp(8))
            alpha = 0.85f
        }
    }

    // ─── Touch Handling ──────────────────────────────────────────────

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        widgetView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = widgetParams?.x ?: 0
                    initialY = widgetParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }

                    widgetParams?.x = initialX - dx
                    widgetParams?.y = initialY + dy

                    try {
                        windowManager?.updateViewLayout(widgetView, widgetParams)
                    } catch (e: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        handleWidgetClick()
                    }
                    true
                }
                else -> false
            }
        }

        widgetView?.setOnLongClickListener {
            handleWidgetLongPress()
            true
        }
    }

    private fun handleWidgetClick() {
        // Always trigger the encryption flow — it handles:
        // - Empty text → Toast
        // - 0 channels → Toast
        // - 1 channel → auto-select + cover message overlay
        // - Multiple channels → channel picker overlay + cover message overlay
        GhostWhisperService.startEncryptionFlow()
    }

    private fun handleWidgetLongPress() {
        if (GhostWhisperService.activeChannelKeyId != null) {
            // Active Channel -> Long press to open Steganography (media encryption)
            val intent =
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "steganography")
                    }
            startActivity(intent)
        } else {
            // Idle -> Long press creates a shortcut to channel list
            val intent =
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "channels")
                        putExtra("quick_select", true)
                    }
            startActivity(intent)
        }
    }

    // ─── State Management ────────────────────────────────────────────

    fun updateState(newState: WidgetState) {
        currentState = newState
        val textView = widgetView as? TextView ?: return

        // Helper to create a rounded background
        fun getRoundedBackground(color: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dpToPx(28).toFloat() // Fully rounded
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            }
        }

        when (newState) {
            WidgetState.IDLE -> {
                textView.text = "👻"
                textView.background = null // Transparent/No background
                textView.alpha = 0.85f
                widgetView?.visibility = View.VISIBLE
            }
            WidgetState.ACTIVE -> {
                textView.text = "👻"
                // Green glow with rounded corners
                textView.background = getRoundedBackground(0xCC_1B5E20.toInt())
                textView.alpha = 1.0f
                widgetView?.visibility = View.VISIBLE
            }
            WidgetState.CHANNEL -> {
                textView.text = "🔒"
                // Purple with rounded corners
                textView.background = getRoundedBackground(0xCC_4A148C.toInt())
                textView.alpha = 1.0f
                widgetView?.visibility = View.VISIBLE
            }
            WidgetState.HIDDEN -> {
                widgetView?.visibility = View.GONE
            }
        }
    }

    private fun removeWidget() {
        widgetView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        widgetView = null
    }

    // ─── Notification ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    "Ghost Whisper Service",
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Keeps Ghost Whisper running in the background"
                                setShowBadge(false)
                            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(com.ghostwhisper.R.string.widget_notification_title))
                .setContentText(getString(com.ghostwhisper.R.string.widget_notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        resources.displayMetrics
                )
                .toInt()
    }
}
