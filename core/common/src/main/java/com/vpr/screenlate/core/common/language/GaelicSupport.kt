package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Scottish Gaelic. Yomitan's descriptor has capitalization only; the bundle adds the app's own rules that undo the
 * initial mutations (lenition, t-s for s, and n-, t- and h- before a vowel: a' ghrian → grian, an t-sràid → sràid, na
 * h-Alba → Alba), and the dictionaries' mutation rows show under the same names ([formTagRules]). Elided dh', m', d',
 * b', th' and bh' are looked up as French l' is (dh'fhàg → fhàg → fàg); apostrophes match each other. Acute vowels of
 * the older spelling are also tried as grave ones (mór → mòr), in any text. People type words without the grave
 * accent at times, so a word without marks also finds the headwords it spells without theirs ([headwordMarks]) when it
 * finds no headword as written ([respelledOnlyWithoutEntry]): bith finds bìth.
 */
object GaelicSupport : SpacedLanguageSupport() {
    override val language = Language.SCOTTISH_GAELIC
    override val glyph = "Ò"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9314"
    override val languageTag = "gd"

    /** Android names the language «гэльский» in Russian, which reads as Irish too. */
    override fun nameIn(locale: Locale): String =
        if (locale.language == "ru") "шотландский гэльский" else super.nameIn(locale)

    override val fontSample = "Ruith an cù bàn sìos an rathad mòr gu Loch Èite."
    override val translationSample = "Tha an t-sìde brèagha an-diugh. An tèid sinn a-mach airson cuairt?"
    override val audioTestWord = "uisge"
    override val pronunciationSample = "uisge → /ˈɯʃkʲə/"
    override val flagCountries = listOf("gb-sct")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "àèìòù"

    /**
     * Lingua Libre has a dozen Gaelic recordings and Wiktionary four, and the phone's usual voice has no Gaelic, so the
     * speaker mostly says there is no audio.
     */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * "m.e." (mar eisimpleir) before an example, often a name; "Urr." (Urramach), "Dr.", "Mgr." (Maighstir) and "Oll."
     * (Ollamh) before a name.
     */
    override val abbreviations = setOf("m.e", "urr", "dr", "mgr", "oll")

    /** Often the last word of a sentence: "msaa." and "msa." (agus mar sin air adhart). */
    override val abbreviationsBeforeLowercase = setOf("msaa", "msa")

    /**
     * dh' (before a vowel or fh), m' (mo), d' (do) and b' (bu), and th' and bh' (tha, bha) written onto the next word:
     * dh'fhàg, m'athair, b'e, th'ann.
     */
    override val elisions = setOf("dh", "m", "d", "b", "th", "bh")

    override val headwordMarks = HeadwordMarks(
        marks = "̀́",
        bases = "aeiou",
        everyHeadword = true,
    )

    override val respelledOnlyWithoutEntry = true

    /**
     * Wiktionary's mutation rows: the English edition's `lenition mutation`, `lenited form`, `lenition`, `eclipsis
     * mutation`, `eclipsis`, `mutation prothesis-h`, `prothesis-h` and `mutation prothesis-t`. It calls t-s for s after
     * the article lenition (an t-sràid) and n- before a vowel eclipsis, as the rules do.
     */
    override val formTagRules = mapOf(
        "lenition mutation" to LENITION,
        "lenited form" to LENITION,
        "lenition" to LENITION,
        "eclipsis mutation" to ECLIPSIS,
        "eclipsis" to ECLIPSIS,
        "mutation prothesis-h" to H_PROTHESIS,
        "prothesis-h" to H_PROTHESIS,
        "mutation prothesis-t" to T_PROTHESIS,
    )

    /** The English edition's `mutation-radical` rows lead from a radical to its mutated form (cill → chill). */
    override val ignoredFormTags = setOf("mutation mutation-radical")

    private const val LENITION = "lenition"
    private const val ECLIPSIS = "eclipsis"
    private const val T_PROTHESIS = "t-prothesis"
    private const val H_PROTHESIS = "h-prothesis"
}
