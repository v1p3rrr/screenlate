package com.vpr.screenlate.overlay.fonts

import com.vpr.screenlate.core.common.language.FontStyle
import com.vpr.screenlate.core.common.language.SystemFonts

/**
 * Font CSS of the lookup page. The phone's font for the language is declared under [SANS] and [SERIF] by its local
 * name and limited to the language's script, so Latin and Cyrillic text keeps the default font. Font names of other
 * systems become aliases of it, and installed fonts are declared under their own family names.
 */
object PageFonts {
    const val SANS = "Screenlate Sans"
    const val SERIF = "Screenlate Serif"

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

    fun fontFaces(system: SystemFonts, installed: List<InstalledFont>): String = buildString {
        fontFace(SANS, localSources(system.sans), unicodeRange = system.unicodeRange)
        fontFace(SERIF, localSources(system.serif), unicodeRange = system.unicodeRange)
        val installedNames = installed.map { normalize(it.family) }.toSet()
        system.aliases.forEach { (alias, style) ->
            if (normalize(alias) in installedNames) return@forEach
            val fallback = if (style == FontStyle.SERIF) system.serif else system.sans
            fontFace(alias, localSources(listOf(alias) + fallback))
        }
        installed.forEach { font ->
            font.files.forEach { file ->
                fontFace(font.family, "url(\"/$DIRECTORY/${file.name}\")", weight = file.weight)
            }
        }
    }

    /** The page's base font list: the chosen font, then the phone's font for the language. */
    fun fontFamily(chosen: InstalledFont?): String =
        listOfNotNull(chosen?.family?.let(::quote), quote(SANS), "sans-serif").joinToString(", ")

    /** Whether the page can show [name]: a generic or Android family, a system font alias, or an installed font. */
    fun isAvailable(name: String, system: SystemFonts, installed: List<InstalledFont>): Boolean {
        val normalized = normalize(name)
        return normalized in BUILT_IN ||
            normalized == normalize(SANS) ||
            normalized == normalize(SERIF) ||
            system.aliases.keys.any { normalize(it) == normalized } ||
            (system.sans + system.serif).any { normalize(it) == normalized } ||
            installed.any { normalize(it.family) == normalized }
    }

    fun quote(name: String): String = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun StringBuilder.fontFace(family: String, src: String, unicodeRange: String? = null, weight: String? = null) {
        append("@font-face { font-family: ").append(quote(family)).append("; src: ").append(src).append(';')
        if (weight != null) append(" font-weight: ").append(weight).append(';')
        if (unicodeRange != null) append(" unicode-range: ").append(unicodeRange).append(';')
        append(" font-display: swap; }\n")
    }

    private fun localSources(names: List<String>): String = names.joinToString(", ") { "local(${quote(it)})" }

    private fun normalize(name: String): String = name.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}
