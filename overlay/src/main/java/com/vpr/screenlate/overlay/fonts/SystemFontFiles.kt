package com.vpr.screenlate.overlay.fonts

import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The phone's own fonts for a language as Android lists them: the files the page's `local()` names resolve to, for the
 * settings preview and for the weights the page may ask of them.
 */
object SystemFontFiles {
    private val weights = ConcurrentHashMap<String, PageFonts.SystemWeights>()

    /** The regular font declared for [languageTag], the sans-serif one unless [serif]; null if the phone has none. */
    fun find(languageTag: String, serif: Boolean = false): Font? = runCatching {
        val language = Locale.forLanguageTag(languageTag).language
        SystemFonts.getAvailableFonts()
            .filter { font -> (0 until font.localeList.size()).any { font.localeList[it].language == language } }
            .filter { isSerif(it) == serif }
            .minByOrNull { abs(it.style.weight - FontStyle.FONT_WEIGHT_NORMAL) }
    }.getOrNull()

    /** The weight ranges of the language's sans-serif and serif fonts where they are variable fonts. Reads files once. */
    fun weights(languageTag: String): PageFonts.SystemWeights = weights.getOrPut(languageTag) {
        PageFonts.SystemWeights(
            sans = find(languageTag)?.let(::weightRange),
            serif = find(languageTag, serif = true)?.let(::weightRange),
        )
    }

    /** The range of [font]'s weight axis as CSS `font-weight`; null for a font with one weight. */
    fun weightRange(font: Font): String? = runCatching {
        val file = font.file ?: return null
        RandomAccessFile(file, "r").use {
            val buffer = it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
            FontFiles.describe(buffer, font.ttcIndex)?.weightRange
        }
    }.getOrNull()

    private fun isSerif(font: Font): Boolean = font.file?.name?.contains("Serif") == true
}
