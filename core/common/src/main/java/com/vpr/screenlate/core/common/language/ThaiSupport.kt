package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Thai: no spaces between words, which ICU's dictionary finds ([SegmentedLanguageSupport]). A space separates
 * sentences and clauses, so it ends the sentence between two Thai letters, except beside the repetition mark ๆ (เด็ก ๆ)
 * and the abbreviation mark ฯ (กรุงเทพฯ, ฯลฯ); a period closes abbreviations (พ.ศ.) and ends nothing after Thai letters.
 * The vowels เ แ โ ใ ไ are written before the consonant they are read after, and า ำ ะ ๅ after it as letters of their
 * own: a lookup neither starts nor ends between them and their consonant. Wiktionary's classifier rows (คน for 386
 * nouns) are left out as the Vietnamese ones. The bundle's text processors read ํา as ำ in recognized text, and put a
 * tone mark typed before the vowel above after it in any text. Lingua Libre has recordings of common words, Wiktionary few.
 */
open class ThaiRules(segmenter: WordSegmenter = IcuWordSegmenter) : SegmentedLanguageSupport(segmenter) {
    override val language = Language.THAI
    override val glyph = "ท"
    override val ocrScript = OcrScript.THAI
    override val wikidataId = "Q9217"
    override val languageTag = "th"
    override val fontSample = "ภาษาไทยมีพยัญชนะสี่สิบสี่ตัว กรุงเทพฯ เป็นเมืองหลวง ฎีกา ฏิมา ฐาน ฒ ฬ"
    override val translationSample = "วันนี้อากาศดีมาก ไปเดินเล่นกันไหม"
    override val audioTestWord = "น้ำ"
    override val pronunciationSample = "น้ำ → /náːm/"
    override val flagCountries = listOf("th")
    override val fontSubsets = setOf("thai")
    override val sentenceTerminators = setOf(' ', ' ', '.', '!', '?', '…', '\n')
    override val quotePairs = mapOf('“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    /**
     * Tesseract reads Thai cluster by cluster. On the comparison screens the gaps at spaces were 0.28-0.74 line heights,
     * those inside words up to 0.37: a space inside a word would break its lookup, a missed one only lengthens a
     * sentence.
     */
    override val ocrSpaceGap = 0.4f

    /** A third of the characters on the hard screens of the OCR comparison, 1% on the clean ones; cloud 0%. */
    override val tesseractMisreadsBusyScreens = true

    /** Wiktionary lists classifiers as forms of the nouns they count: คน of 386 nouns, ตัว of 306. */
    override val ignoredFormTags = setOf("classifier")

    /**
     * The phone's default font falls back to its Thai one (looped) by itself; Noto Serif Thai is reached by name. The
     * range is Google Fonts' Thai subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifThai-Regular", "Noto Serif Thai Regular", "Noto Serif Thai"),
        unicodeRange = "U+02D7, U+0303, U+0331, U+0E01-0E5B, U+200C-200D, U+25CC",
        aliases = emptyMap(),
    )

    override fun inScript(codePoint: Int): Boolean = codePoint in 0x0E01..0x0E5B

    override fun continuesCluster(codePoint: Int): Boolean = isMark(codePoint) || codePoint in FOLLOWING_VOWELS

    override fun holdsNext(codePoint: Int): Boolean = codePoint in 0x0E40..0x0E44

    /** ๆ repeats the word before it and ฯ shortens it; ฯลฯ ("and so on") is a word of its own. */
    override fun mayStartWord(text: String, index: Int): Boolean = when (text[index]) {
        MAI_YAMOK -> false
        PAIYANNOI -> text.startsWith(ETC, index)
        else -> true
    }

    /**
     * A space ends the sentence between Thai letters, not beside ๆ or ฯ, digits or other scripts; a period only after
     * a word of another script, where no letter follows it at once.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        return when (character) {
            ' ', ' ' -> {
                var before = index
                while (before > 0 && text[before - 1].isWhitespace() && text[before - 1] != '\n') before--
                var after = index + 1
                while (after < text.length && text[after].isWhitespace() && text[after] != '\n') after++
                before > 0 && after < text.length && isThaiLetter(text[before - 1]) && isThaiLetter(text[after])
            }
            '.' -> (index == 0 || !inScript(text[index - 1].code)) &&
                (index + 1 >= text.length || !text[index + 1].isLetterOrDigit())
            else -> true
        }
    }

    /** A Thai letter or mark, not a digit, ๆ or ฯ. */
    private fun isThaiLetter(character: Char): Boolean =
        inScript(character.code) && character !in THAI_DIGITS && character != MAI_YAMOK && character != PAIYANNOI

    private companion object {
        /** ะ า ำ ๅ. */
        val FOLLOWING_VOWELS = setOf(0x0E30, 0x0E32, 0x0E33, 0x0E45)
        const val MAI_YAMOK = 'ๆ'
        const val PAIYANNOI = 'ฯ'
        const val ETC = "ฯลฯ"
        val THAI_DIGITS = '๐'..'๙'
    }
}

/** Thai with Android's word segmentation; see [ThaiRules]. */
object ThaiSupport : ThaiRules()
