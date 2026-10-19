package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object SpanishSupport : SpacedLanguageSupport() {
    override val language = Language.SPANISH
    override val glyph = "Ñ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q1321"
    override val languageTag = "es"
    override val fontSample = "El veloz murciélago hindú comía feliz cardillo y kiwi. ¿Qué año? ¡Pingüino!"
    override val translationSample = "Hoy hace buen tiempo. ¿Vamos a dar un paseo?"
    override val audioTestWord = "agua"
    override val pronunciationSample = "agua → /ˈaɡwa/"
    override val flagCountries = listOf("es")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS

    private const val LATIN_AMERICA = "am-lat"

    /** Countries in Wiktionary file titles and transcription tags counted as Latin America. */
    private val LATIN_AMERICAN_COUNTRIES = setOf(
        "mx", "gt", "hn", "sv", "ni", "cr", "pa", "cu", "do", "pr", "co", "ve", "ec", "pe", "bo", "cl", "ar", "uy", "py",
        "us",
    )

    /** Angle quotes as well; ¿ and ¡ open a sentence, they are not brackets. */
    override val quotePairs = super.quotePairs + ('«' to '»')

    /**
     * Wiktionary titles name Latin America as `am-lat` or by a country (`Es-mx-agua.ogg`); recordings from the US
     * count as Latin American.
     */
    override val audioRegions = listOf(
        AudioRegion("latam", setOf(LATIN_AMERICA) + LATIN_AMERICAN_COUNTRIES),
        AudioRegion("es", setOf("es")),
        AudioRegion.OTHER,
    )

    /** Region names in the transcription tags; most of them are flag emoji, which need no entry. */
    override val transcriptionRegionNames = mapOf(
        "Latin-America" to LATIN_AMERICA,
        "America" to LATIN_AMERICA,
        "Central-America" to LATIN_AMERICA,
        "Caribbean" to LATIN_AMERICA,
        "Costa-Rica" to "cr",
        "Dominican-Republic" to "do",
        "Puerto-Rico" to "pr",
        "Texas" to "us",
        "Spain" to "es",
        "Andalusia" to "es",
        "Canary-Islands" to "es",
    )

    /** "mar" (March) is left out: it is also the word for the sea. "p" is "p. ej." written with a space. */
    override val abbreviations = setOf(
        "sr", "sra", "srta", "sres", "dr", "dra", "lic", "ing", "arq", "prof", "ud", "uds", "vd", "vds", "etc", "p.ej",
        "p", "ej", "pág", "págs", "núm", "av", "avda", "cía", "aprox", "admón", "dpto", "depto", "tel", "tlf", "vol",
        "cap", "fig", "art", "atte", "s.a", "ee.uu", "a.c", "d.c", "a.m", "p.m", "ene", "feb", "abr", "jun", "jul",
        "ago", "sep", "sept", "oct", "nov", "dic",
    )
}
