package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Khmer: no spaces between words, which ICU's dictionary finds ([SegmentedLanguageSupport]); a space parts phrases, and
 * sentences end at ។ and ៕. A consonant written below another follows the coeng ្ and goes with the cluster above it
 * (ខ្ញុំ is one cluster of five code points). Text from apps often carries zero-width spaces between words, which ICU
 * takes as boundaries and the bundle's text processor drops from the looked-up text. Subscript DA ្ដ and TA ្ត look
 * alike and dictionaries write both (ក្ដារ, ក្តារ): the bundle reads ្ត as ្ដ, and the import indexes headwords
 * written with ្ត under ្ដ as well ([headwordMarks]). The repetition mark ៗ repeats the word before it. Lingua Libre
 * and Wiktionary have a few recordings (13 and 9 on Commons in October 2026), so they stay ahead of the voice.
 */
open class KhmerRules(segmenter: WordSegmenter = IcuWordSegmenter) : SegmentedLanguageSupport(segmenter) {
    override val language = Language.KHMER
    override val glyph = "ខ"
    override val ocrScript = OcrScript.KHMER
    override val wikidataId = "Q9205"
    override val languageTag = "km"
    override val fontSample = "ភាសាខ្មែរមានព្យញ្ជនៈសាមសិបបីតួ។ ក្រុងភ្នំពេញជារាជធានី ស្ត្រី ក្សត្រ ឪពុក ឫស្សី"
    override val translationSample = "ថ្ងៃនេះអាកាសធាតុល្អណាស់។ ទៅដើរលេងទេ?"
    override val audioTestWord = "ទឹក"
    override val pronunciationSample = "ទឹក → /tɨk/"
    override val flagCountries = listOf("kh")
    override val fontSubsets = setOf("khmer")
    override val sentenceTerminators = setOf('។', '៕', '!', '?', '…', '\n')
    override val quotePairs = mapOf('«' to '»', '“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    /**
     * Tesseract reads Khmer phrase by phrase, so a gap between its words is mostly a space; one that narrow is taken
     * for a gap inside a word, which would break its lookup.
     */
    override val ocrSpaceGap = 0.15f

    override val headwordMarks = HeadwordMarks(
        marks = "",
        bases = "",
        everyHeadword = true,
        replacements = mapOf(SUBSCRIPT_TA to SUBSCRIPT_DA),
    )

    /**
     * The phone's default font falls back to its Khmer one by itself; Noto Serif Khmer is reached by name. The range is
     * Google Fonts' Khmer subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifKhmer-Regular", "Noto Serif Khmer Regular", "Noto Serif Khmer"),
        unicodeRange = "U+1780-17FF, U+19E0-19FF, U+200C-200D, U+25CC",
        aliases = emptyMap(),
    )

    override fun inScript(codePoint: Int): Boolean = codePoint in 0x1780..0x17FF || codePoint in 0x19E0..0x19FF

    /** The coeng puts the next consonant below the one before it. */
    override fun holdsNext(codePoint: Int): Boolean = codePoint == COENG

    /** ៗ repeats the word before it. */
    override fun mayStartWord(text: String, index: Int): Boolean = text[index] != LEK_TOO

    private companion object {
        const val COENG = 0x17D2
        const val LEK_TOO = 'ៗ'
        const val SUBSCRIPT_TA = "្ត"
        const val SUBSCRIPT_DA = "្ដ"
    }
}

/** Khmer with Android's word segmentation; see [KhmerRules]. */
object KhmerSupport : KhmerRules()
