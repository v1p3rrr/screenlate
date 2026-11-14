package com.vpr.screenlate.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.common.settings.isDark
import com.vpr.screenlate.core.common.theme.AppPaletteConfig
import com.vpr.screenlate.core.common.theme.PaletteGenerator

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
 * @param paletteConfig the color palette configuration (preset, custom color, or system wallpaper).
 * @param systemColors legacy parameter for system wallpaper colors (Android 12+).
 * @param eInk e-ink mode: overrides [themeMode] and [paletteConfig], and turns off ripples.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenlateTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    eInk: Boolean = false,
    systemColors: Boolean = false,
    paletteConfig: AppPaletteConfig = AppPaletteConfig(useSystemWallpaper = systemColors),
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark(isSystemInDarkTheme())
    val useSystem = (paletteConfig.useSystemWallpaper || systemColors) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val generatedPalette = remember(paletteConfig) {
        PaletteGenerator.generate(paletteConfig)
    }

    val colorScheme = when {
        eInk -> EInkColorScheme
        useSystem -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> generatedPalette.darkScheme.toColorScheme(isDark = true)
        else -> generatedPalette.lightScheme.toColorScheme(isDark = false)
    }
    val accent = when {
        eInk -> Accent(fill = Color.Black, onFill = Color.White, line = Color.Black)
        useSystem ->
            Accent(fill = colorScheme.primary, onFill = colorScheme.onPrimary, line = colorScheme.primary)
        darkTheme -> generatedPalette.darkAccent.toAccent()
        else -> generatedPalette.lightAccent.toAccent()
    }
    SystemBarIcons(dark = darkTheme && !eInk)
    // One composition path for both modes: switching must keep the screens and their state.
    CompositionLocalProvider(
        LocalEInk provides eInk,
        LocalAccent provides accent,
        LocalRippleConfiguration provides if (eInk) null else LocalRippleConfiguration.current,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * Light status and navigation bar icons on the dark theme, dark ones on the light theme. `enableEdgeToEdge()` alone
 * follows the system's night mode, which differs from the app's theme when one is chosen in Appearance.
 */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
    }
}

/** The scrims `enableEdgeToEdge()` puts behind three-button navigation by default. */
private val LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)

