package com.ghostwhisper.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages the "Post-it Note" style overlay for displaying decrypted messages.
 *
 * Draws on top of all apps using TYPE_APPLICATION_OVERLAY. Auto-dismisses after a configurable
 * timeout.
 */
object OverlayManager {

    private var currentOverlay: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    /** Default auto-dismiss timeout in milliseconds */
    var dismissTimeoutMs: Long = 10_000L

    /** Overlay background color (ARGB) */
    var overlayColor: Int = 0xE6_1A1A2E.toInt() // Dark purple, ~90% opacity

    /**
     * Show a decrypted message overlay on screen.
     *
     * @param context Service context (must be AccessibilityService or similar)
     * @param channelName Which channel the message came from
     * @param message The decrypted plaintext
     * @param onReply Called when the user taps Reply
     * @param onCopy Called when the user taps Copy
     */
    private var lastShownMessage: String? = null
    private var lastShownChannel: String? = null

    /**
     * Show a decrypted message overlay on screen.
     *
     * @param context Service context (must be AccessibilityService or similar)
     * @param channelName Which channel the message came from
     * @param message The decrypted plaintext
     * @param coverMessage The cover message (original text that contained the payload)
     * @param onReply Called when the user taps Reply
     * @param onCopy Called when the user taps Copy
     */
    fun showOverlay(
            context: Context,
            channelName: String,
            message: String,
            coverMessage: String? = null,
            onReply: (() -> Unit)? = null,
            onCopy: (() -> Unit)? = null
    ) {
        // Prevent looping/flickering: if same message & channel are already showing, just extend
        // timeout
        if (currentOverlay != null && channelName == lastShownChannel && message == lastShownMessage
        ) {
            extendTimeout()
            return
        }

        // Remove existing overlay first if different content
        dismissOverlay(context)

        lastShownMessage = message
        lastShownChannel = channelName

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Build the overlay view programmatically (no XML layout needed)
        val overlay =
                buildOverlayView(context, channelName, message, coverMessage, onReply, onCopy) {
                    dismissOverlay(context)
                }

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            y = dpToPx(context, 80)
                        }

        try {
            windowManager.addView(overlay, params)
            currentOverlay = overlay

            // Auto-dismiss after timeout
            scheduleDismiss(context)
        } catch (e: Exception) {
            // Can't show overlay — might not have permission
        }
    }

    private fun scheduleDismiss(context: Context) {
        dismissRunnable = Runnable { dismissOverlay(context) }
        handler.postDelayed(dismissRunnable!!, dismissTimeoutMs)
    }

    private fun extendTimeout() {
        dismissRunnable?.let {
            handler.removeCallbacks(it)
            handler.postDelayed(it, dismissTimeoutMs)
        }
    }

    /** Dismiss the current overlay if one is showing. */
    fun dismissOverlay(context: Context) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null

        currentOverlay?.let { view ->
            try {
                val windowManager =
                        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View might already be removed
            }
        }
        currentOverlay = null
        lastShownMessage = null
        lastShownChannel = null
    }

    /** Build the "Post-it Note" overlay view programmatically. */
    private fun buildOverlayView(
            context: Context,
            channelName: String,
            message: String,
            coverMessage: String?,
            onReply: (() -> Unit)?,
            onCopy: (() -> Unit)?,
            onDismiss: () -> Unit
    ): View {
        val dp = { value: Int -> dpToPx(context, value) }
        val sp = { value: Float -> spToPx(context, value) }

        // Root container
        val root =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(overlayColor)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    elevation = dp(8).toFloat()
                    minimumWidth = dp(280)
                }

        // Header row: channel name + close button
        val headerRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

        val ghostIcon =
                TextView(context).apply {
                    text = "👻 "
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(16f))
                }
        headerRow.addView(ghostIcon)

        val channelLabel =
                TextView(context).apply {
                    text = channelName
                    setTextColor(0xFF_BB86FC.toInt()) // Purple accent
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(14f))
                    layoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
        headerRow.addView(channelLabel)

        val closeBtn =
                TextView(context).apply {
                    text = "  ✕"
                    setTextColor(0xFF_999999.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(18f))
                    setOnClickListener { onDismiss() }
                }
        headerRow.addView(closeBtn)

        root.addView(headerRow)

        // Divider
        val divider =
                View(context).apply {
                    setBackgroundColor(0x33_FFFFFF)
                    layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                                    .apply {
                                        topMargin = dp(8)
                                        bottomMargin = dp(8)
                                    }
                }
        root.addView(divider)

        // Cover Message (if present)
        if (!coverMessage.isNullOrBlank()) {
            val coverLabel =
                    TextView(context).apply {
                        text = "Hidden in: \"$coverMessage\""
                        setTextColor(0xFF_8B949E.toInt()) // Muted text
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f))
                        setPadding(0, 0, 0, dp(4))
                    }
            root.addView(coverLabel)
        }

        // Message body
        val messageView =
                TextView(context).apply {
                    text = message
                    setTextColor(0xFF_FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f))
                    setPadding(0, dp(4), 0, dp(8))
                }
        root.addView(messageView)

        // Action row: Reply + Copy
        val actionRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }

        val replyBtn =
                TextView(context).apply {
                    text = "↩ Reply"
                    setTextColor(0xFF_BB86FC.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(13f))
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        onReply?.invoke()
                        onDismiss()
                    }
                }
        actionRow.addView(replyBtn)

        val copyBtn =
                TextView(context).apply {
                    text = "📋 Copy"
                    setTextColor(0xFF_BB86FC.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(13f))
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                        android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("ghost", message)
                        )
                        onCopy?.invoke()
                        onDismiss()
                    }
                }
        actionRow.addView(copyBtn)

        root.addView(actionRow)

        return root
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        context.resources.displayMetrics
                )
                .toInt()
    }

    private fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.resources.displayMetrics
        )
    }
}
