package com.vpr.screenlate.core.common.language

import java.util.Locale

/**
 * Rules Kazakh and Kyrgyz share: Turkic languages in Cyrillic whose endings the language bundles take off with the
 * app's own rules (`scripts/yomitan-language/own-transforms.js`). An ending after digits or an abbreviation is
 * written with a hyphen (1990-ға, АҚШ-қа, км-ге), and the dictionaries list such endings with it (-ға).
 */
abstract class TurkicCyrillicSupport : SpacedLanguageSupport() {
    final override val ocrScript = OcrScript.CYRILLIC
    override val systemFonts = CYRILLIC_SYSTEM_FONTS

    /** Most letters beyond Russian's (ә, қ, ң, ө, ү) are in the fonts' Cyrillic extension. */
    override val fontSubsets = setOf("latin", "cyrillic", "cyrillic-ext")

    /** «…» outside, „…“ inside, as in Russian; “ closes the inner quotes, so it opens none. */
    override val quotePairs = mapOf('«' to '»', '„' to '“', '(' to ')', '[' to ']')

    /**
     * Wiktionary links Cyrillic headwords to their spellings in the Arabic script, in a romanization and in the Latin
     * alphabet of the 1930s, and Cyrillic letters to their Arabic equivalents (о → و): none of them is read in Cyrillic
     * text.
     */
    override val ignoredFormTags = setOf("Arabic", "romanization", "Yañalif")

    /** A hyphen right before a letter starts a lookup as well, for the dictionaries' suffix entries (-ға). */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.length > 1 && text[0] == '-' && text[1].isLetter()) return LookupStart.Any
        return super.lookupStart(text, latinAsNative)
    }

    /**
     * The text with the language's own letters as their Russian twins ([headwordMarks]), when its first word writes
     * one and the text as written found nothing that long: OCR and keyboards lose some of a word's letters and keep
     * others (Қазакстан for Қазақстан).
     */
    override fun keyedSpelling(text: String, found: Int): String? {
        val twins = headwordMarks?.replacements ?: return null
        var end = 0
        while (end < text.length && text[end].isLetter()) end++
        if (found >= end || (0 until end).none { text[it].toString() in twins }) return null
        return buildString(text.length) { text.forEach { append(twins[it.toString()] ?: it) } }
    }

    override fun keepsWrittenLetters(text: String, headword: String): Boolean {
        val twins = headwordMarks?.replacements ?: return true
        return (0 until minOf(text.length, headword.length)).all { index ->
            text[index].toString() !in twins || text[index].lowercaseChar() == headword[index].lowercaseChar()
        }
    }

    /** Digits, abbreviations with two capitals or more (АҚШ, ҚазМҰУ, NATO) and units (км, кг). */
    override fun takesHyphenedEnding(part: String): Boolean =
        part.all(Char::isDigit) || part.count(Char::isUpperCase) >= 2 || part.lowercase(Locale.ROOT) in UNITS

    private companion object {
        val UNITS = setOf(
            "км", "м", "см", "мм", "кг", "г", "т", "ц", "л", "мл", "га", "млн", "млрд", "трлн", "квт", "мвт", "гвт", "мб",
            "гб", "тб",
        )
    }
}
