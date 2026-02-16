package com.ghostwhisper.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ghostwhisper.data.model.ChannelKey
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "EncryptionOverlay"

/**
 * Manages overlay UIs for the encryption flow — drawn over WhatsApp.
 *
 * Two overlays:
 * 1. Channel Picker (when multiple channels exist)
 * 2. Cover Message Input (always shown before sending)
 *
 * All views use WindowManager TYPE_APPLICATION_OVERLAY so they float on top of WhatsApp without
 * leaving the chat.
 *
 * Thread safety: All show/dismiss operations are posted to the main thread via [handler]. The
 * [isShowing] flag prevents double-add/double-remove race conditions.
 */
object EncryptionOverlayManager {

    private var currentOverlay: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val isShowing = AtomicBoolean(false)

    // ─── Colors ─────────────────────────────────────────────────────
    private const val BG_COLOR = 0xF2_0D1117.toInt() // Dark background ~95% opacity
    private const val ACCENT_COLOR = 0xFF_00E676.toInt() // Ghost green
    private const val TEXT_COLOR = 0xFF_E6EDF3.toInt() // Light text
    private const val MUTED_COLOR = 0xFF_8B949E.toInt() // Muted text
    private const val CARD_COLOR = 0xFF_161B22.toInt() // Card background
    private const val BORDER_COLOR = 0xFF_30363D.toInt() // Border color
    private const val SEND_COLOR = 0xFF_238636.toInt() // Send button green
    private const val CANCEL_COLOR = 0xFF_DA3633.toInt() // Cancel red

    // ─── Channel Picker ─────────────────────────────────────────────

    /**
     * Show a compact overlay listing available channels. Tapping a channel calls [onSelected] with
     * the chosen ChannelKey.
     */
    fun showChannelPicker(
            context: Context,
            channels: List<ChannelKey>,
            onSelected: (ChannelKey) -> Unit,
            onCancel: () -> Unit
    ) {
        dismissOverlaySync(context)

        handler.post {
            try {
                val view = buildChannelPickerView(context, channels, onSelected, onCancel)
                showOverlayView(context, view, focusable = true)
                Log.d(TAG, "Channel picker shown with ${channels.size} channels")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show channel picker: ${e.message}", e)
            }
        }
    }

    // ─── Cover Message Input ────────────────────────────────────────

    /**
     * Show the cover message input overlay.
     *
     * @param context Service context
     * @param channelName Name of the selected channel
     * @param secretText The plaintext being encrypted (shown as preview)
     * @param onSend Called with the cover message when user taps Send
     * @param onCancel Called when user cancels
     */
    fun showCoverMessageInput(
            context: Context,
            channelName: String,
            secretText: String,
            onSend: (coverMessage: String) -> Unit,
            onCancel: () -> Unit
    ) {
        dismissOverlaySync(context)

        handler.post {
            try {
                val view = buildCoverMessageView(context, channelName, secretText, onSend, onCancel)
                showOverlayView(context, view, focusable = true)
                Log.d(TAG, "Cover message overlay shown for channel: $channelName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show cover message overlay: ${e.message}", e)
            }
        }
    }

    // ─── Dismiss ────────────────────────────────────────────────────

    /**
     * Dismiss the current overlay if one is showing. Safe to call from any thread — posts to main.
     */
    fun dismissOverlay(context: Context) {
        handler.post { dismissOverlayInternal(context) }
    }

    /**
     * Dismiss synchronously — removes any pending show callbacks first. Used internally to prevent
     * show-after-dismiss races.
     */
    private fun dismissOverlaySync(context: Context) {
        handler.removeCallbacksAndMessages(null)
        handler.post { dismissOverlayInternal(context) }
    }

    private fun dismissOverlayInternal(context: Context) {
        if (!isShowing.compareAndSet(true, false)) return

        currentOverlay?.let { view ->
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
                Log.d(TAG, "Overlay dismissed")
            } catch (e: Exception) {
                Log.w(TAG, "Overlay already removed: ${e.message}")
            }
        }
        currentOverlay = null
    }

    /**
     * Call from Service.onDestroy() to ensure overlay cleanup. Prevents leaked window manager
     * views.
     */
    fun cleanup(context: Context) {
        handler.removeCallbacksAndMessages(null)
        dismissOverlayInternal(context)
        Log.d(TAG, "Cleanup complete")
    }

    // ─── Build Channel Picker ───────────────────────────────────────

    private fun buildChannelPickerView(
            context: Context,
            channels: List<ChannelKey>,
            onSelected: (ChannelKey) -> Unit,
            onCancel: () -> Unit
    ): View {
        val dp = { v: Int -> dpToPx(context, v) }
        val sp = { v: Float -> spToPx(context, v) }

        val root =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = makeRoundedBackground(BG_COLOR, dp(16))
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                    elevation = dp(12).toFloat()
                    minimumWidth = dp(300)
                }

        // Header
        val header =
                TextView(context).apply {
                    text = "🔐  Select Channel"
                    setTextColor(ACCENT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(18f))
                    setPadding(0, 0, 0, dp(12))
                }
        root.addView(header)

        // Divider
        root.addView(makeDivider(context, dp))

        // Scrollable channel list (capped height so it doesn't fill the screen)
        val maxScrollHeight = dp(250).coerceAtMost(channels.size * dp(50))
        val scrollView =
                ScrollView(context).apply {
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            maxScrollHeight
                                    )
                                    .apply {
                                        topMargin = dp(8)
                                        bottomMargin = dp(8)
                                    }
                }
        val channelList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (channel in channels) {
            val row =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = makeRoundedBackground(CARD_COLOR, dp(10))
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        layoutParams =
                                LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                        .apply { bottomMargin = dp(6) }
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onSelected(channel) }
                    }

            val icon =
                    TextView(context).apply {
                        text = "🔒  "
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(16f))
                    }
            row.addView(icon)

            val nameView =
                    TextView(context).apply {
                        text = channel.channelName
                        setTextColor(TEXT_COLOR)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f))
                        layoutParams =
                                LinearLayout.LayoutParams(
                                        0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        1f
                                )
                    }
            row.addView(nameView)

            val arrow =
                    TextView(context).apply {
                        text = "›"
                        setTextColor(MUTED_COLOR)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(20f))
                    }
            row.addView(arrow)

            channelList.addView(row)
        }

        scrollView.addView(channelList)
        root.addView(scrollView)

        // Cancel button
        val cancelBtn =
                TextView(context).apply {
                    text = "✕  Cancel"
                    setTextColor(CANCEL_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(14f))
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setOnClickListener { onCancel() }
                }
        root.addView(cancelBtn)

        return root
    }

    // ─── Build Cover Message Input ──────────────────────────────────

    private fun buildCoverMessageView(
            context: Context,
            channelName: String,
            secretText: String,
            onSend: (coverMessage: String) -> Unit,
            onCancel: () -> Unit
    ): View {
        val dp = { v: Int -> dpToPx(context, v) }
        val sp = { v: Float -> spToPx(context, v) }

        val root =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = makeRoundedBackground(BG_COLOR, dp(16))
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                    elevation = dp(12).toFloat()
                    minimumWidth = dp(300)
                }

        // Header: Encrypting for channel
        val header =
                TextView(context).apply {
                    text = "🔒  Encrypting for: $channelName"
                    setTextColor(ACCENT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f))
                    setPadding(0, 0, 0, dp(8))
                }
        root.addView(header)

        // Secret text preview (truncated for security — never show full message in overlay)
        val preview = secretText.let { if (it.length > 40) it.take(37) + "…" else it }
        val previewView =
                TextView(context).apply {
                    text = "\"$preview\""
                    setTextColor(MUTED_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f))
                    setPadding(0, 0, 0, dp(12))
                }
        root.addView(previewView)

        // Divider
        root.addView(makeDivider(context, dp))

        // Label
        val label =
                TextView(context).apply {
                    text = "Cover message (visible to everyone):"
                    setTextColor(MUTED_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f))
                    setPadding(0, dp(10), 0, dp(6))
                }
        root.addView(label)

        // Cover message EditText
        val coverInput =
                EditText(context).apply {
                    hint = "Ok / Sure / Sounds good…"
                    setHintTextColor(0xFF_484F58.toInt())
                    setTextColor(TEXT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f))
                    inputType = InputType.TYPE_CLASS_TEXT
                    background = makeRoundedBackground(CARD_COLOR, dp(10))
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    maxLines = 2
                    layoutParams =
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                }
        root.addView(coverInput)

        // Button row
        val btnRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setPadding(0, dp(14), 0, 0)
                }

        // Cancel button
        val cancelBtn =
                TextView(context).apply {
                    text = "Cancel"
                    setTextColor(CANCEL_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(14f))
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    setOnClickListener { onCancel() }
                }
        btnRow.addView(cancelBtn)

        // Send button — disabled after first tap to prevent double-send
        val sendBtn =
                TextView(context).apply {
                    text = "Send 🚀"
                    setTextColor(0xFF_FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f))
                    background = makeRoundedBackground(SEND_COLOR, dp(10))
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    gravity = Gravity.CENTER
                    setOnClickListener { btn ->
                        val coverText = coverInput.text.toString().trim()
                        if (coverText.isNotEmpty()) {
                            btn.isEnabled = false // Prevent double-tap
                            (btn as TextView).text = "Sending…"
                            onSend(coverText)
                        } else {
                            coverInput.error = "Enter a cover message"
                        }
                    }
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply { marginStart = dp(12) }
                }
        btnRow.addView(sendBtn)

        root.addView(btnRow)

        // Auto-focus the EditText
        coverInput.requestFocus()

        return root
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun showOverlayView(context: Context, view: View, focusable: Boolean) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val flags =
                if (focusable) {
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                                flags,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.CENTER
                            softInputMode =
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        }

        try {
            wm.addView(view, params)
            currentOverlay = view
            isShowing.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot show overlay (permission?): ${e.message}")
            isShowing.set(false)
        }
    }

    private fun makeRoundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(1, BORDER_COLOR)
        }
    }

    private fun makeDivider(context: Context, dp: (Int) -> Int): View {
        return View(context).apply {
            setBackgroundColor(BORDER_COLOR)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }
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
