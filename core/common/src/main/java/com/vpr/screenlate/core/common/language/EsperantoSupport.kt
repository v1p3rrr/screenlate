package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * The import keys every headword also without the circumflex of ĉ ĝ ĥ ĵ ŝ and the breve of ŭ ([headwordMarks]), so
 * text typed or read without them finds the words with them (cxu or cu for ĉu), the exact spelling first; the bundle
 * reads the x-system as well (cx for ĉ, ux for ŭ). Ordinals are written with an ending (5-a), not a period.
 */
object EsperantoSupport : SpacedLanguageSupport() {
    override val language = Language.ESPERANTO
    override val glyph = "Ĉ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q143"
    override val languageTag = "eo"
    override val fontSample = "Laŭ Ludoviko Zamenhof bongustas freŝa ĉeĥa manĝaĵo kun spicoj."
    override val translationSample = "Hodiaŭ la vetero estas bela. Ĉu ni promenu?"
    override val audioTestWord = "akvo"
    override val pronunciationSample = "akvo → [ˈakvo]"

    /** The Esperanto flag (a green star on white, on green), not a country's. */
    override val flagCountries = listOf("lang_eo")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ĉĝĥĵŝŭ"

    /**
     * English quotes, „…“ (whose closing character opens English ones) and «…»; »…« and „…” are rarer and not paired.
     */
    override val quotePairs = mapOf('“' to '”', '„' to '“', '«' to '»', '(' to ')', '[' to ']')

    /** Lingua Libre and Wiktionary had recordings of all twenty common words checked; Lingua Libre has more speakers. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** The circumflex of ĉ ĝ ĥ ĵ ŝ and the breve of ŭ. */
    override val headwordMarks = HeadwordMarks(
        marks = "̂",
        bases = "cghjs",
        everyHeadword = true,
        markBases = mapOf("̆" to "u"),
    )

    /**
     * "ekz." (ekzemple), "t.e." (tio estas), "t.n." and "tn." (tiel nomata), "i.a." (inter aliaj), "vd." (vidu), "kp."
     * (komparu), "resp." (respektive), "ĉ." (ĉirkaŭ) and "p." (paĝo) before a number, titles before a name (prof., dr.,
     * inĝ., mag., St. for sankta), "nask." (naskita) and the language labels before a foreign word (angl., lat.).
     */
    override val abbreviations = setOf(
        "ekz", "t.e", "t.n", "tn", "i.a", "vd", "kp", "resp", "ĉ", "p", "prof", "dr", "inĝ", "mag", "st", "nask", "angl",
        "lat", "germ", "franc", "rus", "esp",
    )

    /** The letters of "t. e.", "t. n.", "i. a." and "k. a." written with spaces. */
    override val pairedLetters = setOf("t", "e", "n", "i", "a", "k")

    /**
     * Often a sentence's last word: "ktp." and "k.t.p." (kaj tiel plu), "k.a." and "k.s." (kaj aliaj, kaj simile),
     * "ks." and "kc." (kaj cetere), the eras (a.K., p.K., a.K.E., p.K.E.), "jc." and "jarc." (jarcento), "pp." and
     * "vol." in references, "nr." (numero), "red." and "eld." (redaktita, eldono) after a name, "proks." (proksimume)
     * and units (h., min.).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "ktp", "k.t.p", "k.a", "k.s", "ks", "kc", "k.c", "a.k", "p.k", "a.k.e", "p.k.e", "jc", "jarc", "pp", "vol", "nr",
        "red", "eld", "proks", "h", "min",
    )

    /**
     * Engines read the hat letters as letters of other languages (ŝ as š, ĉ as č or ċ, ĝ as ġ or ğ, ŭ as ǔ, ů or ű, ĥ
     * as ħ), which Esperanto does not write; text read from apps is taken as written, and a Czech or Slovak name read
     * by OCR takes the circumflex (Ŝkoda). ĵ and ĥ read as j and h stay: those are letters too.
     */
    override fun ocrWord(text: String): String = replaceLetters(text, OCR_LETTERS)

    private val OCR_LETTERS = mapOf(
        'Š' to 'Ŝ', 'š' to 'ŝ', 'Č' to 'Ĉ', 'č' to 'ĉ', 'Ċ' to 'Ĉ', 'ċ' to 'ĉ',
        'Ġ' to 'Ĝ', 'ġ' to 'ĝ', 'Ğ' to 'Ĝ', 'ğ' to 'ĝ', 'Ģ' to 'Ĝ', 'ģ' to 'ĝ', 'Ħ' to 'Ĥ', 'ħ' to 'ĥ',
        'Ũ' to 'Ŭ', 'ũ' to 'ŭ', 'Ű' to 'Ŭ', 'ű' to 'ŭ', 'Ů' to 'Ŭ', 'ů' to 'ŭ', 'Ǔ' to 'Ŭ', 'ǔ' to 'ŭ',
    )
}
