package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Wiktionary writes Slovenian forms in the tonemic notation (vôde, dẹ́lał, pə̏s, forms of voda, delati, pes) and its
 * lemma rows without it, so the import keys the forms without the stress marks, with ł and ə written l and e, and stores
 * the lemma names without the marks ([headwordMarks]). Every headword is also keyed without the caron of č š ž, so text
 * typed or read without it finds the words with it, the exact spelling first. Ordinals in digits take a period.
 */
object SlovenianSupport : SpacedLanguageSupport() {
    override val language = Language.SLOVENIAN
    override val glyph = "S"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9063"
    override val languageTag = "sl"
    override val fontSample = "Šerif bo za vajo spet kuhal domače žgance."
    override val translationSample = "Danes je lepo vreme. Gremo na sprehod?"
    override val audioTestWord = "voda"
    override val pronunciationSample = "voda → [ˈvɔda]"
    override val flagCountries = listOf("si")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "čšž"

    /** »…« opens with » and closes with «, the reverse of «…»; „…“ closes with the character English opens with. */
    override val quotePairs = mapOf('»' to '«', '„' to '“', '(' to ')', '[' to ']')

    /** Lingua Libre had recordings of six of twenty common words checked, Wiktionary of two. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Ordinals in digits take a period: "5. maj", "20. stoletje". */
    override val ordinalPeriod = true

    /**
     * The acute, grave, circumflex, double grave, inverted breve, dot below and macron of the tonemic notation (vôde,
     * dẹ́lał, rávnega, ravȃn) after a vowel, r and ə, and its ł and ə, which running text writes l and e; then, for
     * every headword's further key, the caron of č š ž. ć keeps its acute: c is no base.
     */
    override val headwordMarks = HeadwordMarks(
        marks = "\u0301\u0300\u0302\u030F\u0311\u0323\u0304",
        bases = "aeiourə",
        everyHeadword = true,
        replacements = mapOf("ł" to "l", "ə" to "e"),
        lemmasAsWritten = false,
        letterMarks = "\u030C",
        letterBases = "csz",
    )

    /**
     * "npr." (na primer), "t.i." and "tj." (to je), "oz." (oziroma), "mdr." (med drugim), titles before a name (dr.,
     * prof., doc., mag., dipl., inž., g. for gospod, gdč., sv. for sveti), "ul." before a street, "tel." and "str."
     * before a number, "prim." and "gl." (compare, see) and the language labels before a foreign word (angl., nem.,
     * lat.). "ga." (gospa) is left out: ga is a pronoun that often ends a sentence.
     */
    override val abbreviations = setOf(
        "npr", "t.i", "tj", "oz", "mdr", "dr", "prof", "doc", "mag", "dipl", "inž", "univ", "izr", "akad", "msgr", "g",
        "gdč", "sv", "ul", "tel", "str", "prim", "gl", "angl", "ang", "nem", "lat", "fr", "rus", "hrv", "srb", "v.d", "vs",
    )

    /** The letters of "t. i." written with spaces. */
    override val pairedLetters = setOf("t", "i")

    /**
     * Often a sentence's last word: "pr. n. št." and "n. št." (before and in the Common Era), "št." (številka) before a
     * number, "st." and "stol." (stoletje), "l." (leto) before a year, amounts (mio., mrd., tis.), "ok." (okoli), "ur."
     * (urednik, and ur "hours"), "min.", "red." (redni, and red "order"), company forms (d.o.o., d.d., s.p.).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "pr", "n", "št", "pr.n.št", "n.št", "st", "stol", "l", "mio", "mrd", "tis", "ok", "ur", "min", "red", "d.o.o",
        "d.d", "s.p",
    )
}
