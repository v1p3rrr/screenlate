package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan's Welsh descriptor has capitalization only; the bundle adds the app's own rules that undo the initial
 * mutations (soft, nasal, aspirate, h before a vowel: fy nghath → cath, yng Nghymru → Cymru), and the dictionaries'
 * mutation rows show under the same names ([formTagRules]). Words from OCR are also tried with ŷ and ŵ for ý and û,
 * which recognition reads them as; app text and typed text are looked up as written. People often type ŵ and ŷ as w
 * and y, so a word without marks also finds the headwords it spells without theirs ([headwordMarks]) when it finds no
 * headword as written ([respelledOnlyWithoutEntry]): ty finds tŷ, twr only twr. Apostrophes match each other (mae'r,
 * mae’r).
 */
object WelshSupport : SpacedLanguageSupport() {
    override val language = Language.WELSH
    override val glyph = "Ŵ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9309"
    override val languageTag = "cy"
    override val fontSample = "Parciais fy jac codi baw hud llawn dŵr ger tŷ Mabon."
    override val translationSample = "Mae'r tywydd yn braf heddiw. Awn ni am dro?"
    override val audioTestWord = "dŵr"
    override val pronunciationSample = "dŵr → /duːr/"
    override val flagCountries = listOf("gb-wls")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ŵŷ"

    /**
     * Wiktionary had recordings of six of twenty common words checked, Lingua Libre five (mostly place names); the
     * phone's voice reads the rest.
     */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** Wiktionary titles some recordings without the circumflex of ŵ and ŷ (`Cy-dwr.ogg` for dŵr). */
    override fun recordings(term: String, reading: String): List<RecordingTitle> {
        val title = recordingTitle(term, reading)
        return listOf(title, replaceLetters(title, PLAIN_LETTERS)).distinct().map { RecordingTitle(language.code, it) }
    }

    /**
     * "e.e." (er enghraifft), "h.y." (hynny yw), "cf.", "arg." (argraffiad) before a number and "Parch." (Parchedig)
     * before a name.
     */
    override val abbreviations = setOf("e.e", "h.y", "cf", "arg", "parch")

    /** Often the last word of a sentence, after a name: "Ysw." (Yswain) and "O.B.E.". */
    override val abbreviationsBeforeLowercase = setOf("ysw", "o.b.e")

    override val headwordMarks = HeadwordMarks(
        marks = "\u0302\u0300\u0301\u0308",
        bases = "aeiouwy",
        everyHeadword = true,
    )

    override val respelledOnlyWithoutEntry = true

    /**
     * Wiktionary's mutation rows: the English edition's `soft`, `soft mutation`, `nasal mutation`, `aspirate mutation`
     * and `prothesis-h`, the French edition's `lenition` and `nasalization`.
     */
    override val formTagRules = mapOf(
        "soft" to SOFT,
        "soft mutation" to SOFT,
        "soft mutation of" to SOFT,
        "lenition" to SOFT,
        "nasal mutation" to NASAL,
        "nasal mutation of" to NASAL,
        "nasalization" to NASAL,
        "aspirate mutation" to ASPIRATE,
        "prothesis-h" to H_PROTHESIS,
    )

    /** The French edition's `unmutated` rows lead from a radical to its mutated form (te → the). */
    override val ignoredFormTags = setOf("unmutated")

    private const val SOFT = "soft mutation"
    private const val NASAL = "nasal mutation"
    private const val ASPIRATE = "aspirate mutation"
    private const val H_PROTHESIS = "h-prothesis"

    private val PLAIN_LETTERS = mapOf('ŵ' to 'w', 'ŷ' to 'y', 'Ŵ' to 'W', 'Ŷ' to 'Y')
}
