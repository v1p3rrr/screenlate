package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * The bundle undoes the initial mutations with the app's own rules in place of Yomitan's noun eclipsis (lenition,
 * eclipsis, t and h before a vowel: an bhean → bean, i nGaillimh → Gaillimh, an t-uisce → uisce, na hÉireann →
 * Éireann), and the dictionaries' mutation rows show under the same names ([formTagRules]). Elided d', m' and b' are
 * looked up as French l' is (d'fhág → fhág → fág); apostrophes match each other. Irish in Gaelic type (ḃean) is also
 * tried with consonant + h, in any text. A word without the fada also finds the word with it ([foldsMarks]).
 */
object IrishSupport : SpacedLanguageSupport() {
    override val language = Language.IRISH
    override val glyph = "G"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9142"
    override val languageTag = "ga"
    override val fontSample = "Chuaigh bé mhórshách le dlúthspád fíorfhinn trí hata mo dhea-phorcáin bhig."
    override val translationSample = "Tá an aimsir go breá inniu. An rachaimid ag siúl?"
    override val audioTestWord = "uisce"
    override val pronunciationSample = "uisce → /ˈɪʃcə/"
    override val flagCountries = listOf("ie")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "áéíóú"

    /** Lingua Libre has recordings of most common words; no phone voice speaks Irish, so text to speech comes last. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * "m.sh." and "m.s." (mar shampla), "lch." (leathanach) and "uimh." (uimhir) before a number, "Br." (Bráthair) and
     * "Dr." before a name, "Co." (Contae) before a county.
     */
    override val abbreviations = setOf("m.sh", "m.s", "lch", "uimh", "br", "dr", "co")

    /**
     * Often the last word of a sentence: "srl." (agus araile), "Teo." (Teoranta), and "r.n." (roimh nóin) and "i.n."
     * (iarnóin) after a time.
     */
    override val abbreviationsBeforeLowercase = setOf("srl", "teo", "r.n", "i.n")

    /** d' (the past tense, de, do), m' (mo) and b' (ba): d'fhág, m'athair, b'fhéidir. */
    override val elisions = setOf("d", "m", "b")

    /**
     * Wiktionary's mutation rows: the English edition's `eclipsis`, `lenition`, `lenited form` and `prothesis-h`. It
     * calls ts for s after the article lenition (an tSín), as the rules do.
     */
    override val formTagRules = mapOf(
        "eclipsis" to ECLIPSIS,
        "lenition" to LENITION,
        "lenited form" to LENITION,
        "lenition mutation" to LENITION,
        "prothesis-h" to H_PROTHESIS,
    )

    /** The French edition's `radical` rows lead from a radical to its mutated forms (abróimid → n-abróimid). */
    override val ignoredFormTags = setOf("radical", "radical lenition")

    private const val LENITION = "lenition"
    private const val ECLIPSIS = "eclipsis"
    private const val H_PROTHESIS = "h-prothesis"
}
