package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * The bundle adds the app's own endings to Yomitan's rules (adjective forms, mediopassive verbs, participles, noun
 * cases), tried only on a word that neither a headword nor Yomitan's rules find, the first ending that reaches an
 * entry giving the result.
 */
object AlbanianSupport : SpacedLanguageSupport() {
    override val language = Language.ALBANIAN
    override val glyph = "S"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q8748"
    override val languageTag = "sq"
    override val fontSample = "Çdo mëngjes xhaxhai pi kafe të ftohtë në qytetin e vjetër."
    override val translationSample = "Sot moti është i bukur. A të shkojmë për një shëtitje?"
    override val audioTestWord = "shtëpi"
    override val pronunciationSample = "shtëpi → [ʃtəˈpi]"
    override val flagCountries = listOf("al")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "ëç"

    /** English quotes and «…»; „…“ is rare and its “ would clash with the opening “ of English quotes. */
    override val quotePairs = mapOf('“' to '”', '«' to '»', '(' to ')', '[' to ']')

    /**
     * Lingua Libre had recordings of three of twenty common words checked, Wiktionary none; the phone's voice reads
     * the rest.
     */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** s' (negation: s'ka), ç' (what: ç'është), t' (të before a pronoun: t'i, t'u) and m' (më: m'u). */
    override val elisions = setOf("s", "ç", "t", "m")

    /**
     * "p.sh." and "psh." (për shembull), "d.m.th." and "dmth." (domethënë), "nr.", "fq." (faqe), "shek." (shekulli) and
     * "v." (viti) before a number, titles before a name (z., zj., znj., dr., prof., mr., gjen., av., zv.), "rr."
     * (rruga) and the language labels before a foreign word (ang., angl.).
     */
    override val abbreviations = setOf(
        "p.sh", "psh", "d.m.th", "dmth", "nr", "fq", "shek", "v", "z", "zj", "znj", "dr", "prof", "mr", "gjen", "av", "zv",
        "rr", "ang", "angl",
    )

    /**
     * Often a sentence's last word: "etj." (e të tjera), the eras (p.e.s., e.s., p.e.r., e.r.), "a.s." after the
     * Prophet's name, the company forms (sh.a., sh.p.k.), units (m., km., kg.) and the reference labels (f., fig.,
     * red., përg., përkth., bot., vëll.).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "etj", "p.e.s", "e.s", "p.e.r", "e.r", "a.s", "sh.a", "sh.p.k", "m", "km", "kg", "f", "fig", "red", "përg",
        "përkth", "bot", "vëll",
    )

    /** Engines read ë as ě, which Albanian does not write; text read from apps is taken as written. */
    override fun ocrWord(text: String): String = replaceLetters(text, OCR_LETTERS)

    private val OCR_LETTERS = mapOf('Ě' to 'Ë', 'ě' to 'ë')
}
