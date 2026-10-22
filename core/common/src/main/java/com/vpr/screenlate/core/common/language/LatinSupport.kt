package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Wiktionary writes Latin headwords without vowel lengths and names the lemmas of their forms with them (amat, a form
 * of amō), so the import drops macrons and breves from those names ([headwordMarks]); Yomitan's Latin code looks the
 * text up without them as well, and with æ œ as ae oe. The bundle also tries v for a u before a vowel (uita → vita),
 * as scholarly editions write the consonant, and the enclitics -que, -ne, -ve undone (magisque → magis). Those rules
 * and Yomitan's apply only where the text has no entry of its own ([inflectionRulesOnlyWithoutEntry]): atque, sine
 * and regione are words, not at + que, si + ne or regio + ne.
 */
object LatinSupport : SpacedLanguageSupport() {
    override val language = Language.LATIN
    override val glyph = "LA"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q397"
    override val languageTag = "la"
    override val fontSample = "Gallia est omnis dīvīsa in partēs trēs; vīta brevis, ars longa."
    override val translationSample = "Hodie caelum serenum est. Visne ambulare?"
    override val audioTestWord = "magnus"
    override val pronunciationSample = "aqua → [ˈa.kʷa]"

    /** Latin's own flag (the laurel wreath on red), not a country's. */
    override val flagCountries = listOf("lang_la")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS

    /** Vowel lengths and the ligatures; breves and ȳ, which few texts write, are left out. */
    override val fontLetters = "āēīōūæœ"

    /** English quotes and «…» in modern texts; »…« (mostly around titles) is not paired. */
    override val quotePairs = mapOf('“' to '”', '«' to '»', '(' to ')', '[' to ']')

    /** Wiktionary has recordings of more Latin words than Lingua Libre; nearly all name a pronunciation. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /**
     * Wiktionary's Latin recordings name the classical or the ecclesiastical pronunciation (`La-cls-magnus.ogg`,
     * `La-ecc-magnus.ogg`), as its transcriptions do; the few that name neither come after them.
     */
    override val audioRegions = listOf(
        AudioRegion(CLASSICAL, setOf(CLASSICAL)),
        AudioRegion(ECCLESIASTICAL, setOf(ECCLESIASTICAL)),
    )

    /** The church symbol marks the ecclesiastical transcriptions of the Latin edition's words. */
    override val transcriptionRegionNames = mapOf(
        "Classical-Latin" to CLASSICAL,
        "Ecclesiastical" to ECCLESIASTICAL,
        "\u26EA" to ECCLESIASTICAL,
    )

    /** Macrons and breves, which the lemma names of forms carry (amō, Rōma) and the headwords do not. */
    override val headwordMarks = HeadwordMarks(marks = "\u0304\u0306", bases = "aeiouy")

    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "cf.", "e.g.", "i.e.", "sc." (scilicet), "h.e." (hoc est), "s.v." (sub voce), "c." (circa), "p." (pagina),
     * "saec." (saeculum, before a Roman numeral), "ed.", "vol.", "pp.", "cap.", "a.d." (ante diem).
     */
    override val abbreviations = setOf(
        "cf", "e.g", "i.e", "sc", "scil", "h.e", "s.v", "v.gr", "e.gr", "i.a", "q.e", "c", "ca", "p", "pp", "saec", "ed",
        "vol", "cap", "op", "a.d",
    )

    /**
     * The praenomina (C. Iulius Caesar, M. Tullius Cicero, Sex. Pompeius) and S. (sanctus, sancta: S. Mariae), which come
     * before a name; in lowercase they are words or units that end sentences (sex "six", m for metres).
     */
    override val capitalizedAbbreviations = setOf(
        "a", "ap", "cn", "d", "k", "l", "m", "n", "q", "ser", "sex", "sp", "t", "ti", "s",
    )

    /**
     * Often a sentence's last word: the eras "a.C.n." (ante Christum natum), "p.C.n.", "a.C.", "p.C.", "a.u.c." (ab urbe
     * condita), and "etc.", "al." (et al.), "cit." (op. cit.), "sqq.".
     */
    override val abbreviationsBeforeLowercase = setOf("a.c.n", "p.c.n", "a.c", "p.c", "a.u.c", "etc", "al", "cit", "sqq")

    private const val CLASSICAL = "cls"
    private const val ECCLESIASTICAL = "ecc"
}
