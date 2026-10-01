package com.vpr.screenlate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.common.settings.isDark

/** Black and white with one light grey for containers: e-ink screens show few shades and ghost on subtle ones. */
private val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E0E0),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE0E0E0),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color.Black,
    outlineVariant = Color.Black,
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color(0xFFE0E0E0),
    onErrorContainer = Color.Black,
)

/** Whether e-ink mode is on: no animations, black and white. */
val LocalEInk = staticCompositionLocalOf { false }

/**
 * @param systemColors the colors the phone picks (Android 12+, usually from the wallpaper) instead of Screenlate's own.
 * @param eInk e-ink mode: overrides [themeMode] and [systemColors], and turns off ripples.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenlateTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    eInk: Boolean = false,
    systemColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark(isSystemInDarkTheme())
    var system = false
    val colorScheme = when {
        eInk -> EInkColorScheme
        systemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            system = true
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ScreenlateDark
        else -> ScreenlateLight
    }
    val accent = when {
        eInk -> Accent(fill = Color.Black, onFill = Color.White, line = Color.Black)
        // The system's scheme keeps its own primary color on the controls, as before.
        system ->
            Accent(fill = colorScheme.primary, onFill = colorScheme.onPrimary, line = colorScheme.primary)
        darkTheme -> Accent(fill = AccentDark, onFill = OnAccent, line = AccentDark)
        else -> Accent(fill = AccentLightFill, onFill = OnAccent, line = AccentLightLine)
    }
    // One composition path for both modes: switching must keep the screens and their state.
    CompositionLocalProvider(
        LocalEInk provides eInk,
        LocalAccent provides accent,
        LocalRippleConfiguration provides if (eInk) null else LocalRippleConfiguration.current,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
