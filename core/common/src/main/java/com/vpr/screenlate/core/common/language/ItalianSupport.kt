package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary recordings and transcriptions of Italian name no regions, so there are no audio regions. */
object ItalianSupport : SpacedLanguageSupport() {
    override val language = Language.ITALIAN
    override val glyph = "È"
    override val ocrScript = OcrScript.LATIN
    override val iso639Part3 = "ita"
    override val wikidataId = "Q652"
    override val languageTag = "it"
    override val fontSample =
        "Quel vituperabile xenofobo zelante assaggia il whisky ed esclama: alleluja! Perché è già così?"
    override val translationSample = "Oggi fa bel tempo. Andiamo a fare una passeggiata?"
    override val audioTestWord = "acqua"
    override val pronunciationSample = "acqua → /ˈak.kwa/"
    override val flagCountries = listOf("it")
    override val systemFonts = LATIN_SYSTEM_FONTS

    override val quotePairs = super.quotePairs + ('«' to '»')

    /** Elided articles, prepositions and pronouns: l'acqua, dell'anno, c'è, quest'anno. */
    override val elisions = setOf(
        "l", "un", "d", "c", "m", "t", "s", "v", "n", "dell", "all", "dall", "nell", "sull", "coll", "pell", "quest",
        "quell", "bell", "sant", "nessun", "ciascun",
    )

    /** "mar" (March) is left out: it is also a word for the sea. "p" is "p. es." written with a space. */
    override val abbreviations = setOf(
        "sig", "sigg", "sig.ra", "sig.na", "dott", "dott.ssa", "prof", "prof.ssa", "ing", "avv", "arch", "geom", "on",
        "ecc", "es", "p", "p.es", "pag", "pagg", "cap", "fig", "vol", "n", "nr", "tel", "ca", "cfr", "ved", "s.p.a",
        "gen", "feb", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic",
    )
}
