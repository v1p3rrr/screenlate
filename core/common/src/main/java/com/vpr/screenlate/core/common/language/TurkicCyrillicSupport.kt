package com.vpr.screenlate.core.common.language

/**
 * Rules Kazakh and Kyrgyz share: Turkic languages in Cyrillic whose endings the language bundles take off with the
 * app's own rules (`scripts/yomitan-language/own-transforms.js`). An ending after digits or an abbreviation is
 * written with a hyphen (1990-ға, АҚШ-қа, км-ге), and so are some particles (сондай-ақ, келді-ау); the dictionaries
 * list them with it (-ға, -ақ).
 */
abstract class TurkicCyrillicSupport : SpacedLanguageSupport() {
    final override val ocrScript = OcrScript.CYRILLIC
    override val systemFonts = CYRILLIC_SYSTEM_FONTS

    /** Most letters beyond Russian's (ә, қ, ң, ө, ү) are in the fonts' Cyrillic extension. */
    override val fontSubsets = setOf("latin", "cyrillic", "cyrillic-ext")

    /** «…» outside, „…“ inside, as in Russian; “ closes the inner quotes, so it opens none. */
    override val quotePairs = mapOf('«' to '»', '„' to '“', '(' to ')', '[' to ']')

    /**
     * Form rows between scripts. Spellings in the Arabic script, in a romanization and in the Latin alphabet of the
     * 1930s lead to the Cyrillic headword (ىله → Іле, Almaty → Алматы); wty's releases before 2026.10.22 led from the
     * Cyrillic headword to them, and dictionaries built from those still do. Both directions are dropped, so a word in
     * those spellings does not show the Cyrillic one. Cyrillic letters lead to the entries of their Arabic equivalents
     * (а → ا, tagged `Cyrillic`), which a one-letter word (Kyrgyz а, в) would show.
     */
    override val ignoredFormTags = setOf("Arabic", "romanization", "Yañalif", "Cyrillic", "Cyrillic sometimes")

    /**
     * The text with the language's own letters as their Russian twins ([headwordMarks]), when its first word writes
     * one and is not a word as written: OCR and keyboards lose some of a word's letters and keep others (Қазакстан for
     * Қазақстан, Түштук for түштүк).
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

    /**
     * After any part, a hyphen before a letter starting a lookup as well: endings after digits and abbreviations
     * (1990-ға, АҚШ-қа), particles after a word (сондай-ақ).
     */
    override val hyphenedEndings = HyphenedEndings.AFTER_ANY
}
