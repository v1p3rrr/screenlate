package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object FrenchSupport : SpacedLanguageSupport() {
    override val language = Language.FRENCH
    override val glyph = "F"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q150"
    override val languageTag = "fr"
    override val fontSample = "Voix ambiguë d’un cœur qui, au zéphyr, préfère les jattes de kiwis. Où ça ? Là-bas !"
    override val translationSample = "Il fait beau aujourd’hui. On va se promener ?"
    override val audioTestWord = "eau"
    override val pronunciationSample = "eau → /o/"
    override val flagCountries = listOf("fr")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "àâæçéèêëîïôœùûüÿ"

    /** Guillemets as well, with or without the spaces French puts inside them. */
    override val quotePairs = super.quotePairs + ('«' to '»') + ('‹' to '›')

    /** Elided words; "aujourd'hui", "presqu'île" and "quelqu'un" are words of their own. */
    override val elisions = setOf(
        "l", "d", "j", "m", "n", "s", "t", "c", "ç", "qu", "jusqu", "lorsqu", "puisqu", "quoiqu",
    )

    /**
     * Wiktionary titles name Paris as `fr-paris` (`Fr-fr-paris-eau.ogg`) and Canada as `ca`; Belgian titles
     * (`Fr-BE-BW--eau.ogg`) do not match the search and are left out.
     */
    override val audioRegions = listOf(
        AudioRegion("fr", setOf("fr", "fr-paris")),
        AudioRegion("ca", setOf("ca")),
        AudioRegion.OTHER,
    )

    /** Region names in the transcription tags besides flag emoji. */
    override val transcriptionRegionNames = mapOf(
        "Paris" to "fr",
        "Meridional" to "fr",
        "Meridional French" to "fr",
        "Toulouse" to "fr",
        "Provence" to "fr",
        "Saint-Étienne" to "fr",
        "Quebec" to "ca",
        "New Brunswick, Quebec" to "ca",
        "Acadia" to "ca",
        "Louisiana" to "us",
        "Belgium" to "be",
    )

    /** "M." is monsieur; "mars" (March) is a word, not an abbreviation. "p" is "p. ex." written with a space. */
    override val abbreviations = setOf(
        "m", "mm", "mme", "mmes", "mlle", "mlles", "dr", "pr", "st", "ste", "etc", "cf", "env", "av", "apr", "p",
        "pp", "ex", "p.ex", "tél", "vol", "chap", "fig", "éd", "bd", "cie", "janv", "févr", "avr", "juil", "sept",
        "oct", "nov", "déc",
    )
}
