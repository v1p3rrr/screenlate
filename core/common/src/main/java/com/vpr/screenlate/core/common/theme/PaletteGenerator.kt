package com.vpr.screenlate.core.common.theme

/**
 * Generates harmonized Material 3 color schemes, accent pairs, and bubble/popup colors from any seed color.
 *
 * Implements pure Kotlin color generation without Android framework dependencies, ensuring compatibility from
 * Android 7.0 (API 24) to Android 15.
 */
object PaletteGenerator {

    /** Alpha component (45% opacity) applied to the floating bubble disc. */
    const val BUBBLE_ALPHA = 0x73

    /** Dark charcoal tone used for high-contrast text and inverted bubble glyphs on light backgrounds. */
    const val CHARCOAL = 0xFF1C1B20.toInt()

    /** Pure white tone used on dark backgrounds and standard bubble glyphs. */
    const val WHITE = 0xFFFFFFFF.toInt()

    private const val ACCENT_HUE_SHIFT = 15f
    private const val CONTROL_CONTRAST = 3f
    private const val BLACK = 0xFF000000.toInt()

    private val CanonicalVioletLight = SchemeColors(
        primary = 0xFF605597.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        primaryContainer = 0xFFE6DEFF.toInt(),
        onPrimaryContainer = 0xFF483D7E.toInt(),
        inversePrimary = 0xFFCABEFF.toInt(),
        secondary = 0xFF605C71.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        secondaryContainer = 0xFFC4B8FF.toInt(),
        onSecondaryContainer = 0xFF484459.toInt(),
        tertiary = 0xFFA42C7F.toInt(),
        onTertiary = 0xFFFFFFFF.toInt(),
        tertiaryContainer = 0xFFFFD8EB.toInt(),
        onTertiaryContainer = 0xFF860B65.toInt(),
        background = 0xFFFDF8FF.toInt(),
        onBackground = 0xFF1C1B20.toInt(),
        surface = 0xFFFDF8FF.toInt(),
        onSurface = 0xFF1C1B20.toInt(),
        surfaceVariant = 0xFFE6E0EF.toInt(),
        onSurfaceVariant = 0xFF484551.toInt(),
        inverseSurface = 0xFF312F36.toInt(),
        inverseOnSurface = 0xFFF4EFF7.toInt(),
        error = 0xFFBA1A1A.toInt(),
        onError = 0xFFFFFFFF.toInt(),
        errorContainer = 0xFFFFDAD6.toInt(),
        onErrorContainer = 0xFF93000A.toInt(),
        outline = 0xFF797582.toInt(),
        outlineVariant = 0xFFC9C4D3.toInt(),
        surfaceContainerLowest = 0xFFFFFFFF.toInt(),
        surfaceContainerLow = 0xFFF7F2FA.toInt(),
        surfaceContainer = 0xFFF1ECF4.toInt(),
        surfaceContainerHigh = 0xFFEBE6EE.toInt(),
        surfaceContainerHighest = 0xFFE6E1E9.toInt(),
    )

    private val CanonicalVioletDark = SchemeColors(
        primary = 0xFFCABEFF.toInt(),
        onPrimary = 0xFF322666.toInt(),
        primaryContainer = 0xFF483D7E.toInt(),
        onPrimaryContainer = 0xFFE6DEFF.toInt(),
        inversePrimary = 0xFF605597.toInt(),
        secondary = 0xFFC9C3DC.toInt(),
        onSecondary = 0xFF312E41.toInt(),
        secondaryContainer = 0xFF4D4283.toInt(),
        onSecondaryContainer = 0xFFE6DFF9.toInt(),
        tertiary = 0xFFFFAEDB.toInt(),
        onTertiary = 0xFF610048.toInt(),
        tertiaryContainer = 0xFF860B65.toInt(),
        onTertiaryContainer = 0xFFFFD8EB.toInt(),
        background = 0xFF141318.toInt(),
        onBackground = 0xFFE6E1E9.toInt(),
        surface = 0xFF141318.toInt(),
        onSurface = 0xFFE6E1E9.toInt(),
        surfaceVariant = 0xFF484551.toInt(),
        onSurfaceVariant = 0xFFC9C4D3.toInt(),
        inverseSurface = 0xFFE6E1E9.toInt(),
        inverseOnSurface = 0xFF312F36.toInt(),
        error = 0xFFFFB4AB.toInt(),
        onError = 0xFF690005.toInt(),
        errorContainer = 0xFF93000A.toInt(),
        onErrorContainer = 0xFFFFDAD6.toInt(),
        outline = 0xFF938F9C.toInt(),
        outlineVariant = 0xFF484551.toInt(),
        surfaceContainerLowest = 0xFF0F0D13.toInt(),
        surfaceContainerLow = 0xFF1C1B20.toInt(),
        surfaceContainer = 0xFF201F24.toInt(),
        surfaceContainerHigh = 0xFF2B292F.toInt(),
        surfaceContainerHighest = 0xFF36343A.toInt(),
    )

    private val CanonicalVioletLightAccent = AccentColors(
        fill = 0xFFFFC7EC.toInt(),
        onFill = 0xFF3E0029.toInt(),
        line = 0xFFC34799.toInt(),
    )

    private val CanonicalVioletDarkAccent = AccentColors(
        fill = 0xFFFF78CD.toInt(),
        onFill = 0xFF3E0029.toInt(),
        line = 0xFFFF78CD.toInt(),
    )

    /**
     * Generates a complete theme palette based on the given [AppPaletteConfig].
     */
    fun generate(config: AppPaletteConfig): ThemePalette {
        val seed = config.effectiveSeed
        val isCanonicalViolet = config.customSeedColor == null &&
            (config.preset == ColorPreset.VIOLET || config.preset == null)
        return generate(seed, isCanonicalViolet)
    }

    /**
     * Generates a complete theme palette for the given [seedColor].
     *
     * @param isCanonicalViolet if true, uses the hand-crafted classic Screenlate palette for violet #7C5CFF.
     */
    fun generate(
        seedColor: Int,
        isCanonicalViolet: Boolean = false,
    ): ThemePalette {
        val isLight = isBubbleLight(seedColor)
        val fgColor = if (isLight) CHARCOAL else WHITE
        val bColor = bubbleColor(seedColor)

        if (isCanonicalViolet && (seedColor and 0x00FFFFFF) == (ColorPreset.VIOLET.seedColor and 0x00FFFFFF)) {
            return ThemePalette(
                seedColor = seedColor,
                lightScheme = CanonicalVioletLight,
                darkScheme = CanonicalVioletDark,
                lightAccent = CanonicalVioletLightAccent,
                darkAccent = CanonicalVioletDarkAccent,
                bubbleColor = bColor,
                bubbleForegroundColor = fgColor,
                isBubbleLight = isLight,
            )
        }

        val hsl = ColorMath.rgbToHsl(seedColor)
        val h = hsl[0]
        val s = hsl[1]
        val accentH = ColorMath.shiftHue(h, ACCENT_HUE_SHIFT)

        val primarySat = s.coerceIn(0.35f, 0.85f)
        val secondarySat = (primarySat * 0.35f).coerceIn(0.08f, 0.25f)
        val tertiarySat = s.coerceAtMost(0.80f)
        val neutralSat = (primarySat * 0.12f).coerceIn(0.03f, 0.08f)
        val neutralVariantSat = (primarySat * 0.22f).coerceIn(0.06f, 0.14f)

        fun tone(hue: Float, sat: Float, lightness: Float): Int =
            ColorMath.hslToRgb(hue, sat, lightness)

        fun onColor(preferred: Int, background: Int): Int {
            val endpoint = if (ColorMath.contrastRatio(WHITE, background) >= ColorMath.TEXT_CONTRAST) WHITE else BLACK
            return ColorMath.readableForeground(preferred, background, endpoint)
        }

        // --- Light Scheme ---
        val primaryLight = tone(h, primarySat, 0.46f)
        val onPrimaryLight = onColor(WHITE, primaryLight)
        val primaryContainerLight = tone(h, primarySat * 0.65f, 0.92f)
        val onPrimaryContainerLight = tone(h, primarySat, 0.24f)
        val inversePrimaryLight = tone(h, primarySat, 0.82f)

        val secondaryLight = tone(h, secondarySat, 0.42f)
        val onSecondaryLight = onColor(WHITE, secondaryLight)
        val secondaryContainerLight = tone(h, primarySat * 0.55f, 0.86f)
        val onSecondaryContainerLight = tone(h, secondarySat, 0.28f)

        val tertiaryLight = tone(accentH, tertiarySat, 0.42f)
        val onTertiaryLight = onColor(WHITE, tertiaryLight)
        val tertiaryContainerLight = tone(accentH, tertiarySat * 0.55f, 0.92f)
        val onTertiaryContainerLight = tone(accentH, tertiarySat, 0.25f)

        val backgroundLight = tone(h, neutralSat, 0.99f)
        val onBackgroundLight = CHARCOAL
        val surfaceLight = backgroundLight
        val onSurfaceLight = CHARCOAL
        val surfaceVariantLight = tone(h, neutralVariantSat, 0.91f)
        val onSurfaceVariantLight = tone(h, neutralVariantSat, 0.30f)
        val inverseSurfaceLight = tone(h, neutralSat, 0.20f)
        val inverseOnSurfaceLight = tone(h, neutralSat, 0.95f)

        val outlineLight = tone(h, neutralVariantSat, 0.50f)
        val outlineVariantLight = tone(h, neutralVariantSat, 0.80f)
        val surfaceContainerLowestLight = WHITE
        val surfaceContainerLowLight = tone(h, neutralSat, 0.97f)
        val surfaceContainerMidLight = tone(h, neutralSat, 0.94f)
        val surfaceContainerHighLight = tone(h, neutralSat, 0.92f)
        val surfaceContainerHighestLight = tone(h, neutralSat, 0.90f)

        val lightScheme = SchemeColors(
            primary = primaryLight,
            onPrimary = onPrimaryLight,
            primaryContainer = primaryContainerLight,
            onPrimaryContainer = onColor(onPrimaryContainerLight, primaryContainerLight),
            inversePrimary = inversePrimaryLight,
            secondary = secondaryLight,
            onSecondary = onSecondaryLight,
            secondaryContainer = secondaryContainerLight,
            onSecondaryContainer = onColor(onSecondaryContainerLight, secondaryContainerLight),
            tertiary = tertiaryLight,
            onTertiary = onTertiaryLight,
            tertiaryContainer = tertiaryContainerLight,
            onTertiaryContainer = onColor(onTertiaryContainerLight, tertiaryContainerLight),
            background = backgroundLight,
            onBackground = onBackgroundLight,
            surface = surfaceLight,
            onSurface = onSurfaceLight,
            surfaceVariant = surfaceVariantLight,
            onSurfaceVariant = onColor(onSurfaceVariantLight, surfaceVariantLight),
            inverseSurface = inverseSurfaceLight,
            inverseOnSurface = inverseOnSurfaceLight,
            error = 0xFFBA1A1A.toInt(),
            onError = WHITE,
            errorContainer = 0xFFFFDAD6.toInt(),
            onErrorContainer = 0xFF93000A.toInt(),
            outline = outlineLight,
            outlineVariant = outlineVariantLight,
            surfaceContainerLowest = surfaceContainerLowestLight,
            surfaceContainerLow = surfaceContainerLowLight,
            surfaceContainer = surfaceContainerMidLight,
            surfaceContainerHigh = surfaceContainerHighLight,
            surfaceContainerHighest = surfaceContainerHighestLight,
        )

        // --- Dark Scheme ---
        val primaryDark = tone(h, primarySat, 0.82f)
        val onPrimaryDark = tone(h, primarySat, 0.20f)
        val primaryContainerDark = tone(h, primarySat, 0.35f)
        val onPrimaryContainerDark = tone(h, primarySat * 0.65f, 0.92f)
        val inversePrimaryDark = tone(h, primarySat, 0.46f)

        val secondaryDark = tone(h, secondarySat, 0.80f)
        val onSecondaryDark = tone(h, secondarySat, 0.20f)
        val secondaryContainerDark = tone(h, primarySat * 0.55f, 0.38f)
        val onSecondaryContainerDark = tone(h, secondarySat, 0.92f)

        val tertiaryDark = tone(accentH, tertiarySat, 0.80f)
        val onTertiaryDark = tone(accentH, tertiarySat, 0.20f)
        val tertiaryContainerDark = tone(accentH, tertiarySat, 0.32f)
        val onTertiaryContainerDark = tone(accentH, tertiarySat * 0.55f, 0.92f)

        val backgroundDark = tone(h, neutralSat, 0.08f)
        val onBackgroundDark = tone(h, neutralSat, 0.90f)
        val surfaceDark = backgroundDark
        val onSurfaceDark = onBackgroundDark
        val surfaceVariantDark = tone(h, neutralVariantSat, 0.30f)
        val onSurfaceVariantDark = tone(h, neutralVariantSat, 0.80f)
        val inverseSurfaceDark = tone(h, neutralSat, 0.90f)
        val inverseOnSurfaceDark = tone(h, neutralSat, 0.20f)

        val outlineDark = tone(h, neutralVariantSat, 0.60f)
        val outlineVariantDark = tone(h, neutralVariantSat, 0.30f)
        val surfaceContainerLowestDark = tone(h, neutralSat, 0.05f)
        val surfaceContainerLowDark = tone(h, neutralSat, 0.10f)
        val surfaceContainerMidDark = tone(h, neutralSat, 0.13f)
        val surfaceContainerHighDark = tone(h, neutralSat, 0.17f)
        val surfaceContainerHighestDark = tone(h, neutralSat, 0.21f)

        val darkScheme = SchemeColors(
            primary = primaryDark,
            onPrimary = onColor(onPrimaryDark, primaryDark),
            primaryContainer = primaryContainerDark,
            onPrimaryContainer = onColor(onPrimaryContainerDark, primaryContainerDark),
            inversePrimary = inversePrimaryDark,
            secondary = secondaryDark,
            onSecondary = onColor(onSecondaryDark, secondaryDark),
            secondaryContainer = secondaryContainerDark,
            onSecondaryContainer = onColor(onSecondaryContainerDark, secondaryContainerDark),
            tertiary = tertiaryDark,
            onTertiary = onColor(onTertiaryDark, tertiaryDark),
            tertiaryContainer = tertiaryContainerDark,
            onTertiaryContainer = onColor(onTertiaryContainerDark, tertiaryContainerDark),
            background = backgroundDark,
            onBackground = onBackgroundDark,
            surface = surfaceDark,
            onSurface = onSurfaceDark,
            surfaceVariant = surfaceVariantDark,
            onSurfaceVariant = onColor(onSurfaceVariantDark, surfaceVariantDark),
            inverseSurface = inverseSurfaceDark,
            inverseOnSurface = inverseOnSurfaceDark,
            error = 0xFFFFB4AB.toInt(),
            onError = 0xFF690005.toInt(),
            errorContainer = 0xFF93000A.toInt(),
            onErrorContainer = 0xFFFFDAD6.toInt(),
            outline = outlineDark,
            outlineVariant = outlineVariantDark,
            surfaceContainerLowest = surfaceContainerLowestDark,
            surfaceContainerLow = surfaceContainerLowDark,
            surfaceContainer = surfaceContainerMidDark,
            surfaceContainerHigh = surfaceContainerHighDark,
            surfaceContainerHighest = surfaceContainerHighestDark,
        )

        // --- Accents ---
        val fillLight = tone(accentH, tertiarySat, 0.88f)
        val lineLight = ColorMath.readableForeground(
            tone(accentH, tertiarySat * 0.8f, 0.52f), surfaceVariantLight, BLACK, CONTROL_CONTRAST,
        )
        val onFillLight = tone(accentH, tertiarySat, 0.15f)
        val lightAccent = AccentColors(fill = fillLight, onFill = onColor(onFillLight, fillLight), line = lineLight)

        val fillDark = tone(accentH, tertiarySat, 0.72f)
        val lineDark = ColorMath.readableForeground(fillDark, surfaceVariantDark, WHITE, CONTROL_CONTRAST)
        val onFillDark = tone(accentH, tertiarySat, 0.12f)
        val darkAccent = AccentColors(fill = fillDark, onFill = onColor(onFillDark, fillDark), line = lineDark)

        return ThemePalette(
            seedColor = seedColor,
            lightScheme = lightScheme,
            darkScheme = darkScheme,
            lightAccent = lightAccent,
            darkAccent = darkAccent,
            bubbleColor = bColor,
            bubbleForegroundColor = fgColor,
            isBubbleLight = isLight,
        )
    }

    /**
     * Calculates the floating bubble color by combining the 45% alpha channel with the given [seedColor].
     */
    fun bubbleColor(seedColor: Int): Int = (BUBBLE_ALPHA shl 24) or (seedColor and 0x00FFFFFF)

    /**
     * Returns true if the seed color is light enough (> 0.60 luminance) that bubble glyphs, dot, and ring must invert
     * to dark charcoal.
     */
    fun isBubbleLight(seedColor: Int): Boolean = ColorMath.isLightColor(seedColor, threshold = 0.60f)

    /**
     * Foreground color for the bubble ring, center dot, and glyph: [CHARCOAL] if light, otherwise [WHITE].
     */
    fun bubbleForegroundColor(seedColor: Int): Int =
        if (isBubbleLight(seedColor)) CHARCOAL else WHITE
}
