package com.vpr.screenlate.overlay.fonts

import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import com.vpr.screenlate.core.common.language.LanguageSupport
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The phone's own fonts for a language as Android lists them: the files the page's `local()` names resolve to, for the
 * settings preview and for the weights the page may ask of them.
 */
object SystemFontFiles {
    private val weights = ConcurrentHashMap<String, PageFonts.SystemWeights>()

    /**
     * The font the page's `local()` names for [support] resolve to, the sans-serif one unless [serif]. Without one, the
     * regular font declared for the language, which the browser falls back to; null if the phone has none.
     */
    fun find(support: LanguageSupport, serif: Boolean = false): Font? = runCatching {
        val fonts = languageFonts(support.languageTag)
        named(fonts, support, serif)
            ?: fonts.filter { isSerif(it) == serif }.minByOrNull { abs(it.style.weight - FontStyle.FONT_WEIGHT_NORMAL) }
    }.getOrNull()

    /**
     * The weight ranges of the language's sans-serif and serif faces where they are variable fonts. Only a file the
     * `local()` names resolve to counts: a range declared for a font that has one weight would keep the browser from
     * making it bold. Reads files once.
     */
    fun weights(support: LanguageSupport): PageFonts.SystemWeights = weights.getOrPut(support.languageTag) {
        runCatching {
            val fonts = languageFonts(support.languageTag)
            PageFonts.SystemWeights(
                sans = named(fonts, support, serif = false)?.let(::weightRange),
                serif = named(fonts, support, serif = true)?.let(::weightRange),
            )
        }.getOrDefault(PageFonts.SystemWeights())
    }

    /** The range of [font]'s weight axis as CSS `font-weight`; null for a font with one weight. */
    fun weightRange(font: Font): String? = runCatching { FontFiles.describe(font.buffer, font.ttcIndex)?.weightRange }.getOrNull()

    private fun languageFonts(languageTag: String): List<Font> {
        val language = Locale.forLanguageTag(languageTag).language
        return SystemFonts.getAvailableFonts()
            .filter { font -> (0 until font.localeList.size()).any { font.localeList[it].language == language } }
    }

    /** The first of [fonts] whose full or PostScript name is one of the page's `local()` names (case-insensitive). */
    private fun named(fonts: List<Font>, support: LanguageSupport, serif: Boolean): Font? {
        val names = (if (serif) support.systemFonts.serif else support.systemFonts.sans).map { it.lowercase() }.toSet()
        return fonts.firstOrNull { font ->
            runCatching { FontFiles.localNames(font.buffer, font.ttcIndex) }.getOrDefault(emptySet()).any { it.lowercase() in names }
        }
    }

    private fun isSerif(font: Font): Boolean = font.file?.name?.contains("Serif") == true
}
