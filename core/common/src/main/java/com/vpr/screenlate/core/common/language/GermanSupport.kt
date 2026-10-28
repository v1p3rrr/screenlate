package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object GermanSupport : SpacedLanguageSupport() {
    override val language = Language.GERMAN
    override val glyph = "D"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q188"
    override val languageTag = "de"
    override val fontSample = "Victor jagt zwölf Boxkämpfer quer über den großen Sylter Deich."
    override val translationSample = "Heute ist schönes Wetter. Gehen wir spazieren?"
    override val audioTestWord = "Wasser"
    override val pronunciationSample = "Wasser → /ˈvasɐ/"
    override val flagCountries = listOf("de")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "äöüß"

    /** Haustürschlüssel → Haus, Tür, Schlüssel; Arbeitszimmer → Arbeit, Zimmer. */
    override val compoundLinks = listOf("s", "es", "n")
    override val capitalizedNouns = true

    /** „…“ and ‚…‘ close with the characters English opens with; »…« point inwards. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '»' to '«', '(' to ')', '[' to ']')

    /**
     * Recordings from Germany carry no code in Wiktionary titles (`De-Wasser.ogg`) and fall into "other"; Austrian ones
     * are `De-at-…`. The `de` region orders transcriptions tagged with the German flag.
     */
    override val audioRegions = listOf(
        AudioRegion("de", setOf("de")),
        AudioRegion.OTHER,
        AudioRegion("at", setOf("at")),
        AudioRegion("ch", setOf("ch")),
    )

    /**
     * Standard German counts as Germany. Regional variants inside Germany (`Southern-Germany`, `Bavarian`, flags with a
     * mark such as 🇩🇪⬆️ for the north) are left out, so they do not rank above the standard form.
     */
    override val transcriptionRegionNames = mapOf(
        "std" to "de",
        "Standard" to "de",
        "Standard-German" to "de",
        "prescriptive standard" to "de",
        "Austrian" to "at",
    )

    /** Spaced forms such as "z. B." come from [pairedLetters]; "S." (Seite) stands alone. */
    override val abbreviations = setOf(
        "z.b", "d.h", "u.a", "v.a", "o.ä", "u.ä", "s", "usw", "bzw", "ca",
        "vgl", "evtl", "ggf", "inkl", "exkl", "bspw", "sog", "etc", "nr", "str", "dr", "prof", "hr", "fr", "jh", "jhd",
        "mio", "mrd", "tel", "abs", "bd", "kap", "abb", "s.o", "s.u", "z.t", "zt", "u.v.m", "st", "jan", "feb", "febr",
        "apr", "aug", "sept", "okt", "nov", "dez",
    )

    /** The letters of z. B., d. h., u. a., v. a., o. Ä., u. Ä., s. o., z. T., u. v. m., o. g., a. D. */
    override val pairedLetters = setOf("a", "b", "d", "g", "h", "m", "o", "t", "u", "v", "z", "ä")

    /** "am 3. Oktober", "im 19. Jahrhundert". */
    override val ordinalPeriod = true
}
