package com.vpr.screenlate.core.common.theme

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure Kotlin mathematical utilities for color space conversions, luminance, contrast ratios, and theme generation.
 *
 * Designed without Android framework dependencies to ensure full compatibility from Android 7.0 (API 24) to Android 15
 * and fast JVM unit testing.
 */
object ColorMath {

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun rgb(red: Int, green: Int, blue: Int, alpha: Int = 0xFF): Int {
        val a = alpha.coerceIn(0, 0xFF)
        val r = red.coerceIn(0, 0xFF)
        val g = green.coerceIn(0, 0xFF)
        val b = blue.coerceIn(0, 0xFF)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Converts a 32-bit ARGB color integer into HSL components.
     *
     * @param outHsl optional 3-element float array for receiving `[hue, saturation, lightness]`.
     * @return float array with `hue` in `[0f, 360f)`, `saturation` in `[0f, 1f]`, and `lightness` in `[0f, 1f]`.
     */
    fun rgbToHsl(color: Int, outHsl: FloatArray = FloatArray(3)): FloatArray {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f

        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min
        val l = (max + min) / 2f

        val h: Float
        val s: Float
        if (delta == 0f) {
            h = 0f
            s = 0f
        } else {
            s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
            val rawH = when (max) {
                r -> 60f * (((g - b) / delta).rem(6f))
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            h = if (rawH < 0f) rawH + 360f else rawH
        }

        outHsl[0] = (h % 360f + 360f) % 360f
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
        return outHsl
    }

    /**
     * Converts HSL color components into a 32-bit ARGB color integer.
     *
     * @param hue in `[0f, 360f)`.
     * @param saturation in `[0f, 1f]`.
     * @param lightness in `[0f, 1f]`.
     * @param alpha in `0..255`.
     */
    fun hslToRgb(hue: Float, saturation: Float, lightness: Float, alpha: Int = 0xFF): Int {
        val h = (hue % 360f + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)

        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f).rem(2f) - 1f))
        val m = l - c / 2f

        val (rPrime, gPrime, bPrime) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val a = alpha.coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Converts a 32-bit ARGB color integer into HSV components.
     *
     * @param outHsv optional 3-element float array for receiving `[hue, saturation, value]`.
     * @return float array with `hue` in `[0f, 360f)`, `saturation` in `[0f, 1f]`, and `value` in `[0f, 1f]`.
     */
    fun rgbToHsv(color: Int, outHsv: FloatArray = FloatArray(3)): FloatArray {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f

        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min

        val rawH: Float = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta).rem(6f))
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val h = if (rawH < 0f) rawH + 360f else rawH

        val s = if (max == 0f) 0f else delta / max
        val v = max

        outHsv[0] = (h % 360f + 360f) % 360f
        outHsv[1] = s.coerceIn(0f, 1f)
        outHsv[2] = v.coerceIn(0f, 1f)
        return outHsv
    }

    /**
     * Converts HSV color components into a 32-bit ARGB color integer.
     *
     * @param hue in `[0f, 360f)`.
     * @param saturation in `[0f, 1f]`.
     * @param value in `[0f, 1f]`.
     * @param alpha in `0..255`.
     */
    fun hsvToRgb(hue: Float, saturation: Float, value: Float, alpha: Int = 0xFF): Int {
        val h = (hue % 360f + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - abs((h / 60f).rem(2f) - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val a = alpha.coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Perceptual / photometric luminance using ITU-R BT.709 coefficients:
     * `0.2126 * R + 0.7152 * G + 0.0722 * B`.
     *
     * Values range from `0.0f` (black) to `1.0f` (white).
     */
    fun luminance(color: Int): Float {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Relative luminance with sRGB gamma decompression as defined in WCAG 2.1:
     * `L = 0.2126 * R_linear + 0.7152 * G_linear + 0.0722 * B_linear`.
     */
    fun linearLuminance(color: Int): Float {
        fun decompress(c: Float): Float =
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

        val r = decompress(red(color) / 255f)
        val g = decompress(green(color) / 255f)
        val b = decompress(blue(color) / 255f)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Calculates the WCAG contrast ratio between two colors:
     * `(lighter + 0.05) / (darker + 0.05)`.
     *
     * Ranges from `1.0f` (identical) to `21.0f` (black on white).
     */
    fun contrastRatio(colorA: Int, colorB: Int): Float {
        val l1 = linearLuminance(colorA)
        val l2 = linearLuminance(colorB)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Returns true if the color has high luminance (> [threshold]).
     *
     * By default, a threshold of `0.60f` is used to determine whether elements drawn on top of this color (such as the
     * bubble ring, center dot, or glyph) should be inverted to dark charcoal (`#1C1B20`) instead of white (`#FFFFFF`).
     */
    fun isLightColor(color: Int, threshold: Float = 0.60f): Boolean = luminance(color) > threshold

    /**
     * Rotates a hue value by [degrees] along the color circle [0f, 360f).
     */
    fun shiftHue(hue: Float, degrees: Float): Float = ((hue + degrees) % 360f + 360f) % 360f

    /**
     * Formats an ARGB integer to a hexadecimal CSS string: `"#RRGGBB"` or `"#AARRGGBB"`.
     */
    fun toHex(color: Int, includeAlpha: Boolean = false): String {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        val a = alpha(color)
        return if (includeAlpha) {
            String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
        } else {
            String.format(Locale.US, "#%02X%02X%02X", r, g, b)
        }
    }

    /**
     * Formats a color and fractional alpha value into a CSS `rgba(r, g, b, a)` string.
     */
    fun toRgbaString(color: Int, alpha: Float): String {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        val aStr = when {
            clampedAlpha >= 1f -> "1"
            clampedAlpha <= 0f -> "0"
            else -> String.format(Locale.US, "%.2f", clampedAlpha)
        }
        return "rgba($r, $g, $b, $aStr)"
    }

    /**
     * Parses a hex color string (`"#RRGGBB"`, `"RRGGBB"`, `"#AARRGGBB"`, or 3-digit `"#RGB"`).
     * Returns null if the format is invalid.
     */
    fun parseColor(hex: String): Int? {
        val cleaned = hex.trim().removePrefix("#")
        return when (cleaned.length) {
            6 -> cleaned.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> cleaned.toLongOrNull(16)?.toInt()
            3 -> {
                val r = cleaned[0]
                val g = cleaned[1]
                val b = cleaned[2]
                "$r$r$g$g$b$b".toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            }
            else -> null
        }
    }
}
