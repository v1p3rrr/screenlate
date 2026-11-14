package com.vpr.screenlate.core.common.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaletteGeneratorTest {

    @Test
    fun rgbToHslRoundtrip() {
        val testColors = intArrayOf(
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFFFF.toInt(), // White
            0xFF000000.toInt(), // Black
            0xFF808080.toInt(), // Gray
            0xFF7C5CFF.toInt(), // Violet
            0xFF00FFFF.toInt(), // Cyan
            0xFFFF00FF.toInt(), // Magenta
            0xFFFFFF00.toInt(), // Yellow
        )

        for (color in testColors) {
            val hsl = ColorMath.rgbToHsl(color)
            val recovered = ColorMath.hslToRgb(hsl[0], hsl[1], hsl[2])

            val rDiff = abs(ColorMath.red(color) - ColorMath.red(recovered))
            val gDiff = abs(ColorMath.green(color) - ColorMath.green(recovered))
            val bDiff = abs(ColorMath.blue(color) - ColorMath.blue(recovered))

            assertTrue("Red diff $rDiff too large for color ${ColorMath.toHex(color)}", rDiff <= 1)
            assertTrue("Green diff $gDiff too large for color ${ColorMath.toHex(color)}", gDiff <= 1)
            assertTrue("Blue diff $bDiff too large for color ${ColorMath.toHex(color)}", bDiff <= 1)
        }
    }

    @Test
    fun rgbToHsvRoundtrip() {
        val testColors = intArrayOf(
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFFFF.toInt(), // White
            0xFF000000.toInt(), // Black
            0xFF808080.toInt(), // Gray
            0xFF7C5CFF.toInt(), // Violet
            0xFF00FFFF.toInt(), // Cyan
            0xFFFF00FF.toInt(), // Magenta
            0xFFFFFF00.toInt(), // Yellow
            0xFFEA580C.toInt(), // Sunset
        )

        for (color in testColors) {
            val hsv = ColorMath.rgbToHsv(color)
            val recovered = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])

            val rDiff = abs(ColorMath.red(color) - ColorMath.red(recovered))
            val gDiff = abs(ColorMath.green(color) - ColorMath.green(recovered))
            val bDiff = abs(ColorMath.blue(color) - ColorMath.blue(recovered))

            assertTrue("HSV Red diff $rDiff too large for color ${ColorMath.toHex(color)}", rDiff <= 1)
            assertTrue("HSV Green diff $gDiff too large for color ${ColorMath.toHex(color)}", gDiff <= 1)
            assertTrue("HSV Blue diff $bDiff too large for color ${ColorMath.toHex(color)}", bDiff <= 1)
        }
    }

    @Test
    fun luminanceValues() {
        assertEquals(0.0f, ColorMath.luminance(0xFF000000.toInt()), 0.001f)
        assertEquals(1.0f, ColorMath.luminance(0xFFFFFFFF.toInt()), 0.001f)

        val violetLuminance = ColorMath.luminance(0xFF7C5CFF.toInt())
        assertTrue("Violet luminance was $violetLuminance", violetLuminance in 0.40f..0.46f)
        assertFalse(ColorMath.isLightColor(0xFF7C5CFF.toInt()))

        val yellowLuminance = ColorMath.luminance(0xFFFDE047.toInt())
        assertTrue("Yellow luminance was $yellowLuminance", yellowLuminance > 0.80f)
        assertTrue(ColorMath.isLightColor(0xFFFDE047.toInt()))

        val cyanLuminance = ColorMath.luminance(0xFF67E8F9.toInt())
        assertTrue("Cyan luminance was $cyanLuminance", cyanLuminance > 0.75f)
        assertTrue(ColorMath.isLightColor(0xFF67E8F9.toInt()))

        val navyLuminance = ColorMath.luminance(0xFF1E3A8A.toInt())
        assertTrue("Navy luminance was $navyLuminance", navyLuminance < 0.30f)
        assertFalse(ColorMath.isLightColor(0xFF1E3A8A.toInt()))
    }

    @Test
    fun bubbleContrastInversion() {
        // Dark colors keep white glyph/ring/dot.
        val darkSeeds = intArrayOf(
            0xFF7C5CFF.toInt(), // Violet
            0xFF1E3A8A.toInt(), // Navy
            0xFF059669.toInt(), // Emerald
            0xFF000000.toInt(), // Black
            0xFF0284C7.toInt(), // Ocean
        )
        for (seed in darkSeeds) {
            assertFalse("Expected dark for ${ColorMath.toHex(seed)}", PaletteGenerator.isBubbleLight(seed))
            assertEquals(PaletteGenerator.WHITE, PaletteGenerator.bubbleForegroundColor(seed))
        }

        // Light colors invert to dark charcoal #1C1B20.
        val lightSeeds = intArrayOf(
            0xFFFDE047.toInt(), // Pastel yellow
            0xFF67E8F9.toInt(), // Light cyan
            0xFFFFFFFF.toInt(), // White
            0xFFFEF08A.toInt(), // Light amber/yellow
        )
        for (seed in lightSeeds) {
            assertTrue("Expected light for ${ColorMath.toHex(seed)}", PaletteGenerator.isBubbleLight(seed))
            assertEquals(PaletteGenerator.CHARCOAL, PaletteGenerator.bubbleForegroundColor(seed))
        }
    }

    @Test
    fun bubbleColorAlpha() {
        val seed = 0xFF7C5CFF.toInt()
        val bubbleColor = PaletteGenerator.bubbleColor(seed)
        assertEquals(0x73, ColorMath.alpha(bubbleColor))
        assertEquals(ColorMath.red(seed), ColorMath.red(bubbleColor))
        assertEquals(ColorMath.green(seed), ColorMath.green(bubbleColor))
        assertEquals(ColorMath.blue(seed), ColorMath.blue(bubbleColor))
        assertEquals(0x737C5CFF, bubbleColor)
    }

    @Test
    fun canonicalVioletPalette() {
        val config = AppPaletteConfig(preset = ColorPreset.VIOLET)
        val palette = PaletteGenerator.generate(config)

        assertEquals(0xFF605597.toInt(), palette.lightScheme.primary)
        assertEquals(0xFFFFFFFF.toInt(), palette.lightScheme.onPrimary)
        assertEquals(0xFFE6DEFF.toInt(), palette.lightScheme.primaryContainer)
        assertEquals(0xFFCABEFF.toInt(), palette.darkScheme.primary)
        assertEquals(0xFF322666.toInt(), palette.darkScheme.onPrimary)

        assertEquals(0xFFFFC7EC.toInt(), palette.lightAccent.fill)
        assertEquals(0xFFC34799.toInt(), palette.lightAccent.line)
        assertEquals(0xFFFF78CD.toInt(), palette.darkAccent.fill)
        assertEquals(0xFF3E0029.toInt(), palette.darkAccent.onFill)

        assertEquals("#7C5CFF", palette.popupAccent)
        assertEquals("rgba(124, 92, 255, 0.18)", palette.popupMatchedBg)
        assertEquals("rgba(124, 92, 255, 0.55)", palette.popupBorder)
    }

    @Test
    fun allEightPresetsGenerateContrastCompliantPalettes() {
        for (preset in ColorPreset.entries) {
            val config = AppPaletteConfig(preset = preset)
            val palette = PaletteGenerator.generate(config)

            assertEquals(preset.seedColor, palette.seedColor)

            // Primary and onPrimary contrast
            val lightPrimaryContrast = ColorMath.contrastRatio(
                palette.lightScheme.onPrimary,
                palette.lightScheme.primary,
            )
            assertTrue(
                "Light onPrimary contrast $lightPrimaryContrast too low for ${preset.id}",
                lightPrimaryContrast >= 3.0f,
            )

            val darkPrimaryContrast = ColorMath.contrastRatio(
                palette.darkScheme.onPrimary,
                palette.darkScheme.primary,
            )
            assertTrue(
                "Dark onPrimary contrast $darkPrimaryContrast too low for ${preset.id}",
                darkPrimaryContrast >= 3.0f,
            )

            // Surface and onSurface contrast
            val lightSurfaceContrast = ColorMath.contrastRatio(
                palette.lightScheme.onSurface,
                palette.lightScheme.surface,
            )
            assertTrue(
                "Light onSurface contrast $lightSurfaceContrast too low for ${preset.id}",
                lightSurfaceContrast >= 4.5f,
            )

            val darkSurfaceContrast = ColorMath.contrastRatio(
                palette.darkScheme.onSurface,
                palette.darkScheme.surface,
            )
            assertTrue(
                "Dark onSurface contrast $darkSurfaceContrast too low for ${preset.id}",
                darkSurfaceContrast >= 4.5f,
            )

            // Accent fill and onFill contrast
            val lightAccentContrast = ColorMath.contrastRatio(
                palette.lightAccent.onFill,
                palette.lightAccent.fill,
            )
            assertTrue(
                "Light accent contrast $lightAccentContrast too low for ${preset.id}",
                lightAccentContrast >= 4.5f,
            )

            val darkAccentContrast = ColorMath.contrastRatio(
                palette.darkAccent.onFill,
                palette.darkAccent.fill,
            )
            assertTrue(
                "Dark accent contrast $darkAccentContrast too low for ${preset.id}",
                darkAccentContrast >= 4.5f,
            )

            // Verify accent hue is shifted
            val primaryHsl = ColorMath.rgbToHsl(preset.seedColor)
            val accentHsl = ColorMath.rgbToHsl(palette.lightAccent.fill)
            val hueDifference = abs(primaryHsl[0] - accentHsl[0]).let { if (it > 180f) 360f - it else it }
            assertTrue(
                "Accent hue shift too small (${hueDifference}°) for ${preset.id}",
                hueDifference > 20f,
            )
        }
    }

    @Test
    fun extremeCustomColorsDoNotCrash() {
        val extremeSeeds = intArrayOf(
            0xFF000000.toInt(), // Pure black
            0xFFFFFFFF.toInt(), // Pure white
            0xFFFF0000.toInt(), // Pure red
            0xFF00FF00.toInt(), // Pure green
            0xFF0000FF.toInt(), // Pure blue
            0xFFFFFF00.toInt(), // Pure yellow
            0xFF7F7F7F.toInt(), // 50% gray
        )

        for (seed in extremeSeeds) {
            val palette = PaletteGenerator.generate(
                AppPaletteConfig(preset = null, customSeedColor = seed),
            )
            assertEquals(seed, palette.seedColor)
            assertNotNull(palette.lightScheme)
            assertNotNull(palette.darkScheme)
            assertNotNull(palette.lightAccent)
            assertNotNull(palette.darkAccent)
            assertNotNull(palette.popupAccent)
            assertNotNull(palette.popupMatchedBg)
            assertNotNull(palette.popupBorder)
        }
    }

    @Test
    fun colorParsingAndFormatting() {
        assertEquals(0xFF7C5CFF.toInt(), ColorMath.parseColor("#7C5CFF"))
        assertEquals(0xFF0284C7.toInt(), ColorMath.parseColor("0284C7"))
        assertEquals(0x737C5CFF, ColorMath.parseColor("#737C5CFF"))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.parseColor("#FFF"))
        assertNull(ColorMath.parseColor("ZZZZZZ"))
        assertNull(ColorMath.parseColor("#12"))

        assertEquals("#7C5CFF", ColorMath.toHex(0xFF7C5CFF.toInt()))
        assertEquals("#737C5CFF", ColorMath.toHex(0x737C5CFF, includeAlpha = true))

        assertEquals("rgba(124, 92, 255, 0.18)", ColorMath.toRgbaString(0xFF7C5CFF.toInt(), 0.18f))
        assertEquals("rgba(124, 92, 255, 0.55)", ColorMath.toRgbaString(0xFF7C5CFF.toInt(), 0.55f))
        assertEquals("rgba(0, 0, 0, 1)", ColorMath.toRgbaString(0xFF000000.toInt(), 1f))
        assertEquals("rgba(255, 255, 255, 0)", ColorMath.toRgbaString(0xFFFFFFFF.toInt(), 0f))
    }
}
