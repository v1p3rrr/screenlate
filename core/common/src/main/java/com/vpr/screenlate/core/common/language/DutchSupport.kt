package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Wiktionary recordings of Dutch name no regions (some titles carry a speaker's initials there, `Nl-tv-hond.ogg`), so
 * there are no audio regions.
 */
object DutchSupport : SpacedLanguageSupport() {
    override val language = Language.DUTCH
    override val glyph = "Ĳ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q7411"
    override val languageTag = "nl"
    override val fontSample = "Pa’s wijze lynx bezag vroom het fikse aquaduct."
    override val translationSample = "Het is mooi weer vandaag. Zullen we gaan wandelen?"
    override val audioTestWord = "water"
    override val pronunciationSample = "water → /ˈʋaː.tər/"
    override val flagCountries = listOf("nl")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "éëïóöü"

    /** Stadsdeel → stad, deel; boekenkast → boeken, kast; the old spelling pannekoek → panne, koek. */
    override val compoundLinks = listOf("s", "en", "e")

    /** „…” closes with the character English closes with. */
    override val quotePairs = mapOf('“' to '”', '„' to '”', '(' to ')', '[' to ']')

    /**
     * Abbreviations that are also words or names ending sentences are left out: jan (Jan), tel (count!). Spaced forms
     * such as "d. w. z." are rare in Dutch and not handled.
     */
    override val abbreviations = setOf(
        "bijv", "bv", "d.w.z", "o.a", "m.a.w", "e.d", "enz", "etc", "ca", "nr", "blz", "dhr", "mevr", "mr", "dr",
        "prof", "ir", "ing", "drs", "jl", "a.s", "t.o.v", "i.p.v", "m.b.t", "i.v.m", "n.a.v", "t.a.v", "a.u.b", "z.s.m",
        "vgl", "evt", "resp", "incl", "excl", "zgn", "jr", "sr", "st", "vs",
        "feb", "mrt", "apr", "aug", "sep", "sept", "okt", "nov", "dec",
    )
}
