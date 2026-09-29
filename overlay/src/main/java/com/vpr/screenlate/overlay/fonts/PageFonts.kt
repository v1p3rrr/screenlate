package com.vpr.screenlate.overlay.fonts

import com.vpr.screenlate.core.common.language.FontStyle
import com.vpr.screenlate.core.common.language.SystemFonts

/**
 * Font CSS of the lookup page. The phone's font for the language is declared under [SANS] and [SERIF] by its local
 * name and limited to the language's script, so Latin and Cyrillic text keeps the default font. Font names of other
 * systems become aliases of it, and installed fonts are declared under their own family names for custom CSS. The
 * chosen font is declared once more under [CHOSEN], which the page uses, so another installed font with the same
 * family name cannot take its place.
 */
object PageFonts {
    const val SANS = "Screenlate Sans"
    const val SERIF = "Screenlate Serif"
    const val CHOSEN = "Screenlate Chosen"

    /** Folder under the app's files where installed fonts live; the page loads them from `/fonts/<name>`. */
    const val DIRECTORY = "fonts"

    /** Generic families and the names Android resolves by itself (the families and aliases of its fonts.xml). */
    private val BUILT_IN = setOf(
        "serif", "sans-serif", "monospace", "cursive", "fantasy", "system-ui", "ui-serif", "ui-sans-serif",
        "ui-monospace", "ui-rounded", "math", "emoji", "fangsong", "inherit", "initial", "unset", "revert",
        "casual", "roboto", "roboto-flex", "sans-serif-condensed", "sans-serif-smallcaps", "serif-monospace",
        "source-sans-pro", "sans-serif-black", "sans-serif-light", "sans-serif-medium", "sans-serif-thin",
        "sans-serif-monospace", "sans-serif-condensed-light", "sans-serif-condensed-medium", "serif-bold",
        "source-sans-pro-semi-bold", "arial", "helvetica", "tahoma", "verdana", "times", "times new roman", "georgia",
        "palatino", "baskerville", "goudy", "itc stone serif", "courier", "courier new", "monaco",
    )

    /**
     * Weight ranges of the phone's fonts that are variable fonts, so the page can ask them for any weight; null for a
     * font with one weight, which the browser makes bold by itself.
     */
    data class SystemWeights(val sans: String? = null, val serif: String? = null)

    /**
     * @param chosen the installed font the page uses, declared as [CHOSEN].
     * @param scriptOnly [chosen] applies only to the language's script.
     */
    fun fontFaces(
        system: SystemFonts,
        installed: List<InstalledFont>,
        chosen: InstalledFont? = null,
        scriptOnly: Boolean = false,
        weights: SystemWeights = SystemWeights(),
    ): String = buildString {
        fontFace(SANS, localSources(system.sans), unicodeRange = system.unicodeRange, weight = weights.sans)
        fontFace(SERIF, localSources(system.serif), unicodeRange = system.unicodeRange, weight = weights.serif)
        val installedNames = installed.map { normalize(it.family) }.toSet()
        system.aliases.forEach { (alias, style) ->
            if (normalize(alias) in installedNames) return@forEach
            val fallback = if (style == FontStyle.SERIF) system.serif else system.sans
            fontFace(alias, localSources(listOf(alias) + fallback))
        }
        installed.forEach { font ->
            font.files.forEach { file ->
                fontFace(font.family, fileSource(file), weight = file.weight)
            }
        }
        chosen?.files?.forEach { file ->
            fontFace(CHOSEN, fileSource(file), unicodeRange = system.unicodeRange.takeIf { scriptOnly }, weight = file.weight)
        }
    }

    /** The page's base font list: the chosen font ([CHOSEN]), then the phone's font for the language. */
    fun fontFamily(chosen: InstalledFont?): String =
        listOfNotNull(chosen?.let { quote(CHOSEN) }, quote(SANS), "sans-serif").joinToString(", ")

    /** Whether [value] is a CSS `font-weight` as font lists store it: one number or a range. */
    fun isWeight(value: String): Boolean = WEIGHT.matches(value)

    /** Whether the page can show [name]: a generic or Android family, a system font alias, or an installed font. */
    fun isAvailable(name: String, system: SystemFonts, installed: List<InstalledFont>): Boolean {
        val normalized = normalize(name)
        return normalized in BUILT_IN ||
            normalized == normalize(SANS) ||
            normalized == normalize(SERIF) ||
            normalized == normalize(CHOSEN) ||
            system.aliases.keys.any { normalize(it) == normalized } ||
            (system.sans + system.serif).any { normalize(it) == normalized } ||
            installed.any { normalize(it.family) == normalized }
    }

    /** [name] as a CSS string; control characters, which would end it, are dropped. */
    fun quote(name: String): String =
        "\"" + name.filterNot { it.isISOControl() }.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun StringBuilder.fontFace(family: String, src: String, unicodeRange: String? = null, weight: String? = null) {
        append("@font-face { font-family: ").append(quote(family)).append("; src: ").append(src).append(';')
        if (weight != null) append(" font-weight: ").append(weight).append(';')
        if (unicodeRange != null) append(" unicode-range: ").append(unicodeRange).append(';')
        append(" font-display: swap; }\n")
    }

    private fun fileSource(file: FontFile): String = "url(\"/$DIRECTORY/${file.name}\")"

    private fun localSources(names: List<String>): String = names.joinToString(", ") { "local(${quote(it)})" }

    private val WEIGHT = Regex("[1-9][0-9]{0,3}( [1-9][0-9]{0,3})?")

    private fun normalize(name: String): String = name.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}
