package com.example.audio_bible.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary            = BibleAmber,
    onPrimary          = BibleDarkBrown,
    primaryContainer   = BibleCardDark,
    onPrimaryContainer = BibleAmberLight,
    secondary          = BibleGold,
    onSecondary        = BibleDarkBrown,
    secondaryContainer = Color(0xFF3A2A10.toInt()),
    onSecondaryContainer = BibleGoldLight,
    tertiary           = BibleRust,
    background         = BibleDarkBrown,
    onBackground       = BibleCream,
    surface            = BibleDarkSurface,
    onSurface          = BibleCream,
    surfaceVariant     = BibleCardDark,
    onSurfaceVariant   = Color(0xFFCFB88A),
    outline            = Color(0xFF7A5C2E)
)

private val LightColorScheme = lightColorScheme(
    primary            = BibleBrown,
    onPrimary          = BibleCream,
    primaryContainer   = BibleLightCard,
    onPrimaryContainer = BibleBrown,
    secondary          = BibleGold,
    onSecondary        = BibleCream,
    secondaryContainer = BibleLightSurface,
    onSecondaryContainer = BibleBrown,
    tertiary           = BibleRust,
    background         = BibleLightBg,
    onBackground       = BibleBrown,
    surface            = BibleLightSurface,
    onSurface          = BibleBrown,
    surfaceVariant     = BibleLightCard,
    onSurfaceVariant   = BibleBrownLight,
    outline            = BibleBrownLight
)

@Composable
fun AudiobibleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
