package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object PortugueseSupport : SpacedLanguageSupport() {
    override val language = Language.PORTUGUESE
    override val glyph = "Ã"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q5146"
    override val languageTag = "pt"
    override val fontSample =
        "Luís argüia à Júlia que «brações, fé, chá, óxido, pôr, zângão» eram palavras do português."
    override val translationSample = "Hoje está um dia bonito. Vamos dar um passeio?"
    override val audioTestWord = "água"
    override val pronunciationSample = "água → /ˈa.ɡwɐ/"
    override val flagCountries = listOf("br", "pt")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "áâãàçéêíóôõú"

    override val quotePairs = super.quotePairs + ('«' to '»')

    /** "d'água" in gota d'água and copo-d'água. */
    override val elisions = setOf("d")

    /** Wiktionary titles name Brazil as `br` and Portugal as `pt` (`Pt-br-como.ogg`). */
    override val audioRegions = listOf(
        AudioRegion("br", setOf("br")),
        AudioRegion("pt", setOf("pt")),
        AudioRegion.OTHER,
    )

    /**
     * Accents in the transcription tags besides plain flags. The general Brazilian and European forms come first in the
     * dictionary, tagged with the flag; regional ones (🇧🇷RdJ for Rio de Janeiro, Lisbon) follow them in their country.
     */
    override val transcriptionRegionNames = mapOf(
        "br" to listOf(
            "Brazilian", "🇧🇷RdJ", "🇧🇷SP", "🇧🇷C", "🇧🇷🎯", "🇧🇷↗️", "🇧🇷⬆️", "🇧🇷M-G", "Southern-Brazil", "Rio de Janeiro",
            "Rural Central Brazil", "Rio Grande do Sul",
        ),
        "pt" to listOf(
            "European", "Lisbon", "Porto", "Minho", "Alentejo", "Alentejo, Portugal", "Alentejo, Algarve", "Azores",
            "São Miguel, Azores", "Madeira", "Portugal, natural speech", "Portugal, faster speech",
        ),
        "ao" to listOf("Angola"),
    ).flatMap { (region, names) -> names.map { it to region } }.toMap()

    /** "mar" (March) is left out: it is also the word for the sea. "p" is "p. ex." written with a space. */
    override val abbreviations = setOf(
        "sr", "sra", "srta", "dr", "dra", "prof", "profa", "eng", "exmo", "exma", "v.exa", "sto", "sta", "av", "pág",
        "págs", "p", "ex", "p.ex", "etc", "n", "tel", "vol", "cap", "fig", "art", "cia", "ltda", "s.a", "aprox", "jan",
        "fev", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez",
    )
}
