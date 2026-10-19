package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings of Czech name no regions (`Cs-kočka.ogg`), so there are no audio regions. */
object CzechSupport : SpacedLanguageSupport() {
    override val language = Language.CZECH
    override val glyph = "Ř"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9056"
    override val languageTag = "cs"
    override val fontSample = "Příliš žluťoučký kůň úpěl ďábelské ódy."
    override val translationSample = "Dnes je hezké počasí. Půjdeme na procházku?"
    override val audioTestWord = "voda"
    override val pronunciationSample = "voda → [ˈvoda]"
    override val flagCountries = listOf("cz")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS

    /** Wiktionary has recordings of more Czech words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** „…“ and ‚…‘ close with the characters English opens with; »…« point inwards. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '»' to '«', '(' to ')', '[' to ']')

    /** Ordinals in digits take a period: "5. května", "20. století". */
    override val ordinalPeriod = true

    /**
     * "r." and "st." are rok and století ("r. 1990", "19. st."), "s." and "str." strana, "č." číslo. Abbreviations that
     * are also words ending sentences are left out: nám (náměstí, and "us"), tis (tisíc, and yew), mil (milion, and
     * of miles).
     */
    override val abbreviations = setOf(
        "např", "tj", "tzv", "tzn", "apod", "atd", "resp", "mj", "aj", "popř", "příp", "cca", "č", "s", "str", "r",
        "st", "ul", "tel", "mudr", "ing", "mgr", "bc", "dr", "doc", "prof", "phdr", "judr", "rndr", "př", "mld",
    )

    /** The letters of "př. n. l." and "n. l." (before and in the Common Era). */
    override val pairedLetters = setOf("n", "l")
}
