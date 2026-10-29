package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Uzbek, in its Latin alphabet. Yomitan has nothing for it; the language bundle borrows Turkish's language entry
 * without its case rules (Uzbek cases i and I as English does) and adds the app's own Turkic ending rules
 * (kitoblarimizda → kitob, boraman → bormoq), the apostrophes written as the dictionaries write them (' ‘ ’ ` ´ as ʻ
 * after o and g, as ʼ elsewhere: so'z → soʻz, ta'lim → taʼlim) and Cyrillic text in Latin letters (китоб → kitob).
 * Headwords with oʻ and gʻ are also found without the ʻ, which OCR and quick typing drop ([headwordMarks]): bolsa
 * finds boʻlsa, while a written oʻ finds only itself. An ending after a number or an abbreviation in capitals with a
 * hyphen (2020-yil, BMT-da) is also looked up with the hyphen.
 */
object UzbekSupport : SpacedLanguageSupport() {
    override val language = Language.UZBEK
    override val glyph = "O"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9264"
    override val languageTag = "uz"
    override val fontSample = "Bogʻdagi oʻriklar gulladi, maʼlumotlarga koʻra, ertaga qattiq shamol xavfi bor."
    override val translationSample = "Bugun havo juda yaxshi. Sayrga chiqamizmi?"
    override val audioTestWord = "yaxshi"
    override val flagCountries = listOf("uz")

    /** The turned comma ʻ and the apostrophe ʼ are modifier letters, outside the Latin blocks. */
    override val systemFonts = LATIN_SYSTEM_FONTS.copy(unicodeRange = LATIN_SYSTEM_FONTS.unicodeRange + ", U+02BB-02BC")
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ʻʼ"

    /** Lingua Libre has recordings of a few hundred words; Wiktionary of a few dozen. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** English quotes mostly, «…» too. */
    override val quotePairs = super.quotePairs + ('«' to '»')

    /** Typed and recognized text writes the turned comma and the apostrophe as ‘ ` ´ too, inside the word. */
    override val wordConnectors = super.wordConnectors + "‘`´"

    /**
     * Headwords also without the ʻ of oʻ and gʻ, and with their apostrophes as the language bundle writes those of the
     * text: so'rmoq is also found as soʻrmoq and sormoq, ta`til as taʼtil.
     */
    override val headwordMarks = HeadwordMarks(
        marks = APOSTROPHE_SPELLINGS + TURNED_COMMA + APOSTROPHE,
        bases = "og",
        everyHeadword = true,
        replacements = buildMap {
            for (letter in listOf("o", "g", "O", "G")) {
                for (mark in APOSTROPHE_SPELLINGS + APOSTROPHE) put("$letter$mark", "$letter$TURNED_COMMA")
            }
            for (mark in APOSTROPHE_SPELLINGS) put("$mark", "$APOSTROPHE")
        },
    )

    /** The ending rules would put davo "remedy" (-m, my) beside davom "continuation". */
    override val inflectionRulesOnlyWithoutEntry = true

    /** Also with the word forms alone: davom is a word of the English dictionary, though uz–ru lacks it. */
    override val formsHeadwordsCountAsEntries = true

    /**
     * Before a name "prof.", "dots." (dotsent) and "akad." (akademik); in addresses, before the street's name or the
     * number, "sh." (shahri, city), "q." (qishlogʻi, village) and "koʻch." (koʻchasi, street), also with the
     * characters typed for ʻ; "mil." of "mil. av." (miloddan avvalgi, before our era).
     */
    override val abbreviations =
        setOf("prof", "dots", "akad", "sh", "q", "koʻch", "mil") + APOSTROPHE_SPELLINGS.map { "ko${it}ch" }

    override val abbreviationApostrophes = APOSTROPHE_SPELLINGS

    /**
     * Often a sentence's last word: "va h.k." (va hokazo, and so on), "va b." (va boshqalar, and others), "av." of
     * "mil. av."; after a number "y." (yil, year), "mln.", "mlrd.".
     */
    override val abbreviationsBeforeLowercase = setOf("h.k", "b", "av", "y", "mln", "mlrd")

    /** Endings after a number or an abbreviation in capitals: 2020-yil, 5-iyun, BMT-ning. */
    override val hyphenedEndings = HyphenedEndings.AFTER_NUMBERS_AND_CAPITALS

    private const val TURNED_COMMA = "ʻ"
    private const val APOSTROPHE = "ʼ"

    /** Characters typed or recognized for ʻ and ʼ. */
    private const val APOSTROPHE_SPELLINGS = "'`´‘’"
}
