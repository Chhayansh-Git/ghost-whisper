package com.ghostwhisper.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("ghost_settings", Context.MODE_PRIVATE)

    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(value) = prefs.edit { putBoolean("app_lock_enabled", value) }

    var chaffingEnabled: Boolean
        get() = prefs.getBoolean("chaffing_enabled", false)
        set(value) = prefs.edit { putBoolean("chaffing_enabled", value) }

    var clipboardGuardEnabled: Boolean
        get() = prefs.getBoolean("clipboard_guard_enabled", true)
        set(value) = prefs.edit { putBoolean("clipboard_guard_enabled", value) }

    var overlayTimeout: Float
        get() = prefs.getFloat("overlay_timeout", 10f)
        set(value) = prefs.edit { putFloat("overlay_timeout", value) }

    fun getCoverMessages(): List<String> {
        val set = prefs.getStringSet("cover_messages", null)
        return set?.toList()
                ?: listOf(
                        "Noted 👍",
                        "Understood",
                        "Interesting point",
                        "Makes sense",
                        "OK",
                        "Got it"
                )
    }

    fun addCoverMessage(message: String) {
        val current = getCoverMessages().toMutableSet()
        current.add(message)
        prefs.edit { putStringSet("cover_messages", current) }
    }

    fun removeCoverMessage(message: String) {
        val current = getCoverMessages().toMutableSet()
        current.remove(message)
        prefs.edit { putStringSet("cover_messages", current) }
    }
}
