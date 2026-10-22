package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Norwegian as written in Bokmål: its dictionaries are Wiktionary's Bokmål, Nynorsk and Norwegian ones (nb, nn, no in
 * the catalog), and the services that tell the written forms apart (translation, text to speech) get Bokmål's nb.
 * Wiktionary recordings of Norwegian name no regions (`No-vann.ogg`), so there are no audio regions.
 */
object NorwegianSupport : SpacedLanguageSupport() {
    override val language = Language.NORWEGIAN
    override val glyph = "Ø"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9043"
    override val languageTag = "nb"
    override val speechTag = "nb"
    override val localeLanguages = setOf("no", "nb", "nn")
    override val fontSample = "Vår sære Zulu fra badeøya spilte jo whist og quickstep i min taxi."
    override val translationSample = "Det er fint vær i dag. Skal vi gå en tur?"
    override val audioTestWord = "vann"
    override val pronunciationSample = "vann → [ʋanː]"
    override val flagCountries = listOf("no")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val quotePairs = mapOf('«' to '»', '‘' to '’', '“' to '”', '(' to ')', '[' to ']')

    /** Lingua Libre files its Norwegian recordings under Norwegian; Bokmål's item is asked as well. */
    override val linguaLibreItems = listOf("Q9043", "Q25167")

    /** Lingua Libre has recordings of more Norwegian words than Wiktionary. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Fylkesmann → fylke, mann; arbeidsplass → arbeid, plass. */
    override val compoundLinks = listOf("s", "e")

    /** Ordinals in digits take a period: "5. mai", "20. århundre". */
    override val ordinalPeriod = true

    /** Abbreviations that keep the sentence going; st. mostly comes before a name (St. Petersburg). */
    override val abbreviations = setOf(
        "f.eks", "bl.a", "dvs", "d.v.s", "ca", "nr", "kl", "pga", "evt", "ev", "mht", "jf", "inkl", "ekskl", "iflg",
        "ifm", "vha", "tlf", "st",
    )

    /**
     * A word as well (s), or often the last word of a sentence (f.Kr., osv., m.fl.): they keep it going only before a
     * number or a lowercase word.
     */
    override val abbreviationsBeforeLowercase = setOf(
        "s", "f.kr", "e.kr", "osv", "o.l", "m.m", "mv", "m.v", "m.fl", "mfl", "etc",
    )

    /** OCR's ả, â and ä are å ([ScandinavianLetters]); text read from apps is taken as written. */
    override fun ocrWord(text: String): String = ScandinavianLetters.danishNorwegian(text)
}
