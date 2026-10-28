package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings and transcriptions of Polish name no regions, so there are no audio regions. */
object PolishSupport : SpacedLanguageSupport() {
    override val language = Language.POLISH
    override val glyph = "P"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q809"
    override val languageTag = "pl"
    override val fontSample = "Pchnąć w tę łódź jeża lub ośm skrzyń fig. Źdźbło, żółw, gęś."
    override val translationSample = "Dziś jest ładna pogoda. Pójdziemy na spacer?"
    override val audioTestWord = "woda"
    override val pronunciationSample = "woda → /ˈvɔ.da/"
    override val flagCountries = listOf("pl")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ąćęłńóśźż"

    /** Wiktionary has recordings of more Polish words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** „…” opens low and closes high; «…» for quotes inside quotes. */
    override val quotePairs = super.quotePairs + ('„' to '”') + ('«' to '»')

    /** Ordinals in digits take a period: "5. rocznica". */
    override val ordinalPeriod = true

    /**
     * "r", "w" and "s" are rok, wiek and strona ("2020 r.", "XX w.", "s. 5"). Abbreviations that are also words ending
     * sentences are left out: im (them), lis (fox), maj (May), rys (trait).
     */
    override val abbreviationsBeforeLowercase = setOf("ok")

    override val abbreviations = setOf(
        "np", "itd", "itp", "tj", "tzn", "tzw", "m.in", "ang", "łac", "wg", "zob", "por", "jw", "cdn", "dr",
        "prof", "mgr", "inż", "hab", "doc", "ks", "św", "płk", "gen", "ul", "al", "pl", "nr", "tel", "str", "s", "r",
        "w", "godz", "min", "woj", "pow", "gm", "płn", "płd", "wsch", "zach", "sp", "o.o", "tys", "mln", "mld", "zł",
        "gr", "wyd", "red", "t", "tab", "sty", "lut", "kwi", "cze", "wrz", "paź", "gru",
    )
}
