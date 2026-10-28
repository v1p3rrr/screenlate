package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings of Swedish name no regions (`Sv-vatten.ogg`), so there are no audio regions. */
object SwedishSupport : SpacedLanguageSupport() {
    override val language = Language.SWEDISH
    override val glyph = "S"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9027"
    override val languageTag = "sv"
    override val fontSample = "Flygande bäckasiner söka hwila på mjuka tuvor."
    override val translationSample = "Det är fint väder i dag. Ska vi gå på promenad?"
    override val audioTestWord = "vatten"
    override val pronunciationSample = "vatten → /ˈvatːɛn/"
    override val flagCountries = listOf("se")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "åäö"

    /** Wiktionary has recordings of more Swedish words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /**
     * Statsminister → stat, minister; skattepengar → skatt, pengar; folkparti → folk, parti. A part cut short before its
     * link (kyrko- in kyrkogård, of kyrka) is not tried.
     */
    override val compoundLinks = listOf("s", "e", "u", "o", "a")

    /** Long compounds: 0.93% of the words of news and Wikipedia text are longer than 16 letters. */
    override val defaultScanLength = 24

    /**
     * Abbreviations that keep the sentence going; st. mostly comes before a name (St. Louis). The forms written with
     * spaces and no periods ("t ex", "bl a") end nothing anyway.
     */
    override val abbreviations = setOf(
        "t.ex", "bl.a", "s.k", "f.d", "dvs", "d.v.s", "p.g.a", "pga", "fr.o.m", "t.o.m", "kl", "nr", "jfr", "resp", "ca",
        "inkl", "exkl", "st",
    )

    /**
     * Words as well (ung "young", s), or often the last word of a sentence (f.Kr., m.fl., osv.): they keep it going
     * only before a number or a lowercase word.
     */
    override val abbreviationsBeforeLowercase = setOf("s", "ung", "m.fl", "f.kr", "e.kr", "osv", "m.m", "etc")

    /** OCR's ả and â are å ([ScandinavianLetters]); text read from apps is taken as written. */
    override fun ocrWord(text: String): String = ScandinavianLetters.swedish(text)
}
