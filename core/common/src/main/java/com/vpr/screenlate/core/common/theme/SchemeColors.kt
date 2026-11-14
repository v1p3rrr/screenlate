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
    private val lightPopupAccent = readablePopupAccent(
        seedColor, 0xFFF8F6FD.toInt(), lightScheme.surfaceContainerHigh, 0xFF000000.toInt(),
    )
    private val darkPopupAccent = readablePopupAccent(
        darkScheme.primary, 0xFF1F1D24.toInt(), darkScheme.surfaceContainerHigh, PaletteGenerator.WHITE,
    )

    val popupAccentLight: String get() = ColorMath.toHex(lightPopupAccent)
    val popupAccentDark: String get() = ColorMath.toHex(darkPopupAccent)
    val popupMatchedBgLight: String get() = ColorMath.toRgbaString(lightPopupAccent, 0.18f)
    val popupMatchedBgDark: String get() = ColorMath.toRgbaString(darkPopupAccent, 0.18f)
    val popupBorderLight: String get() = ColorMath.toRgbaString(lightPopupAccent, 0.55f)
    val popupBorderDark: String get() = ColorMath.toRgbaString(darkPopupAccent, 0.55f)

    private fun readablePopupAccent(seed: Int, background: Int, chip: Int, endpoint: Int): Int {
        val readable = ColorMath.readableForeground(seed, background, endpoint)
        return ColorMath.readableForeground(readable, chip, endpoint)
    }

    /** Light chip background in `#RRGGBB` format for popup CSS `--chip-bg` in light mode. */
    val popupChipBgLight: String
        get() = ColorMath.toHex(lightScheme.surfaceContainerHigh)

    /** Dark chip background in `#RRGGBB` format for popup CSS `--chip-bg` in dark mode. */
    val popupChipBgDark: String
        get() = ColorMath.toHex(darkScheme.surfaceContainerHigh)

    /** Light dictionary name chip background in `#RRGGBB` format for popup CSS `--dictionary-bg`. */
    val popupDictionaryBgLight: String
        get() = ColorMath.toHex(lightScheme.primaryContainer)

    /** Light dictionary name chip foreground in `#RRGGBB` format for popup CSS `--dictionary-fg`. */
    val popupDictionaryFgLight: String
        get() = ColorMath.toHex(lightScheme.onPrimaryContainer)

    /** Dark dictionary name chip background in `#RRGGBB` format for popup CSS `--dictionary-bg`. */
    val popupDictionaryBgDark: String
        get() = ColorMath.toHex(darkScheme.primaryContainer)

    /** Dark dictionary name chip foreground in `#RRGGBB` format for popup CSS `--dictionary-fg`. */
    val popupDictionaryFgDark: String
        get() = ColorMath.toHex(darkScheme.onPrimaryContainer)
}
