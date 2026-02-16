package com.ghostwhisper.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GhostDarkColorScheme =
        darkColorScheme(
                primary = GhostPurple,
                onPrimary = Color.White,
                primaryContainer = GhostPurpleDeep,
                onPrimaryContainer = GhostPurple,
                secondary = GhostTeal,
                onSecondary = Color.Black,
                secondaryContainer = GhostGreenDark,
                onSecondaryContainer = GhostGreen,
                tertiary = GhostGreen,
                onTertiary = Color.Black,
                error = GhostRed,
                onError = Color.Black,
                background = DarkBackground,
                onBackground = TextPrimary,
                surface = DarkSurface,
                onSurface = TextPrimary,
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = TextSecondary,
                outline = TextMuted
        )

@Composable
fun GhostWhisperTheme(content: @Composable () -> Unit) {
    val colorScheme = GhostDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = GhostTypography, content = content)
}
