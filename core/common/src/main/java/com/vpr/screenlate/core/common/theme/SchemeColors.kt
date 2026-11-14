package com.vpr.screenlate.core.common.theme

/**
 * 32-bit ARGB integer values for Material 3 color roles.
 */
data class SchemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
)

/**
 * Accent colors for selection controls, progress indicators, switches, and highlighted buttons.
 *
 * @property fill large areas: switch track, checked box, chosen segment, main button.
 * @property onFill content on [fill].
 * @property line thin marks: radio dot, slider thumb/track, progress bars.
 */
data class AccentColors(
    val fill: Int,
    val onFill: Int,
    val line: Int,
)

/**
 * Complete generated theme palette containing light and dark schemes, accents, and floating bubble/popup colors.
 */
data class ThemePalette(
    val seedColor: Int,
    val lightScheme: SchemeColors,
    val darkScheme: SchemeColors,
    val lightAccent: AccentColors,
    val darkAccent: AccentColors,
    val bubbleColor: Int,
    val bubbleForegroundColor: Int,
    val isBubbleLight: Boolean,
) {
    /** Primary brand color in `#RRGGBB` format for popup CSS `--accent`. */
    val popupAccent: String
        get() = ColorMath.toHex(seedColor)

    /** Matched text background in `rgba(r, g, b, 0.18)` format for popup CSS `--matched-bg`. */
    val popupMatchedBg: String
        get() = ColorMath.toRgbaString(seedColor, 0.18f)

    /** Border color in `rgba(r, g, b, 0.55)` format for popup CSS `--border`. */
    val popupBorder: String
        get() = ColorMath.toRgbaString(seedColor, 0.55f)

    /** Light chip background in `#RRGGBB` format for popup CSS `--chip-bg` in light mode. */
    val popupChipBgLight: String
        get() = ColorMath.toHex(lightScheme.surfaceContainerHigh)

    /** Dark chip background in `#RRGGBB` format for popup CSS `--chip-bg` in dark mode. */
    val popupChipBgDark: String
        get() = ColorMath.toHex(darkScheme.surfaceContainerHigh)
}
