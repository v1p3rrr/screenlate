package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings of Danish name no regions (`Da-vand.ogg`), so there are no audio regions. */
object DanishSupport : SpacedLanguageSupport() {
    override val language = Language.DANISH
    override val glyph = "Æ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9035"
    override val languageTag = "da"
    override val fontSample = "Høj bly gom vandt fræk sexquiz på wc."
    override val translationSample = "Det er godt vejr i dag. Skal vi gå en tur?"
    override val audioTestWord = "hund"
    override val pronunciationSample = "hund → [hunˀ]"
    override val flagCountries = listOf("dk")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS

    /**
     * »…« opens with » and closes with «, the reverse of «…»; English quotes are common too. „…“ is rare and its “
     * would clash with the opening “ of English quotes, so it is not paired.
     */
    override val quotePairs = mapOf('»' to '«', '“' to '”', '(' to ')', '[' to ']')

    /** Lingua Libre has recordings of more Danish words than Wiktionary. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Statsminister → stat, minister; skolebørn → skole, børn. */
    override val compoundLinks = listOf("s", "e")

    /** Ordinals in digits take a period: "5. maj", "20. århundrede". */
    override val ordinalPeriod = true

    /** Abbreviations that keep the sentence going; st. mostly comes before a name (St. Petersborg). */
    override val abbreviations = setOf(
        "f.eks", "bl.a", "dvs", "d.v.s", "ca", "nr", "kl", "pga", "p.g.a", "evt", "mht", "m.h.t", "jf", "hhv", "stk",
        "inkl", "ekskl", "vedr", "ift", "i.f.t", "iflg", "ifm", "tlf", "st",
    )

    /**
     * A word as well (s), or often the last word of a sentence (f.Kr., osv., mv.): they keep it going only before a
     * number or a lowercase word.
     */
    override val abbreviationsBeforeLowercase = setOf(
        "s", "f.kr", "e.kr", "osv", "mv", "m.v", "m.m", "etc", "o.l",
    )

    /** OCR's ả, â and ä are å ([ScandinavianLetters]); text read from apps is taken as written. */
    override fun ocrWord(text: String): String = ScandinavianLetters.danishNorwegian(text)
}
