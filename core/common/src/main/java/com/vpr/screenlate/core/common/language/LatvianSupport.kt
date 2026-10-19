package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings of Latvian name no regions, so there are no audio regions. */
object LatvianSupport : SpacedLanguageSupport() {
    override val language = Language.LATVIAN
    override val glyph = "Ā"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9078"
    override val languageTag = "lv"
    override val fontSample = "Glāžšķūņa rūķīši dzērumā čiepj Baha koncertflīģeļu vākus."
    override val translationSample = "Šodien ir jauks laiks. Vai iesim pastaigāties?"
    override val audioTestWord = "ūdens"
    override val flagCountries = listOf("lv")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS

    /** „…“ closes with the character English opens with; «…» for quotes inside quotes. */
    override val quotePairs = mapOf('„' to '“', '«' to '»', '(' to ')', '[' to ']')

    /** Ordinals in digits take a period: "18. novembris", "2. klase". */
    override val ordinalPeriod = true

    /**
     * "g" and "gs" are gads and gadsimts ("1918. g.", "20. gs."). Abbreviations that are also words ending sentences are
     * left out: aug (grows), min (treads, guesses).
     */
    override val abbreviations = setOf(
        "u.c", "utt", "u.t.t", "u.tml", "piem", "t.i", "t.sk", "sk", "sal", "apm", "plkst", "g", "gs", "nr", "lpp",
        "tālr", "prof", "dr", "doc", "iel", "tūkst", "milj", "mljrd", "angl", "lat", "red", "izd", "att", "tab",
        "janv", "febr", "apr", "sept", "okt", "nov", "dec",
    )
}
