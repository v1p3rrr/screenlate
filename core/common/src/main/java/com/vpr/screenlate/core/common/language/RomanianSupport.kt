package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Wiktionary stores Romanian forms without the letters' marks (numarul, a form of număr), which Yomitan's Romanian
 * code also looks the text up without; the import keys every headword without ă â î ș ț as well ([headwordMarks]), so
 * text typed without them finds the words with them (dupa → după), the exact spelling first. The cedilla letters ş ţ
 * that older keyboards type are read as ș ț. The app's own article and agreement endings reach the words whose entries
 * list no forms (consiliul → consiliu), only where the text has no entry of its own ([inflectionRulesOnlyWithoutEntry]).
 */
object RomanianSupport : SpacedLanguageSupport() {
    override val language = Language.ROMANIAN
    override val glyph = "Ș"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q7913"
    override val languageTag = "ro"
    override val fontSample = "Muzicologă în bej vând whisky și tequila, preț fix."
    override val translationSample = "Astăzi este vreme frumoasă. Mergem la plimbare?"
    override val audioTestWord = "apă"
    override val pronunciationSample = "apă → [ˈapə]"
    override val flagCountries = listOf("ro")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ăâîșț"

    /** „…” outside, «…» inside; English quotes are common too. „…“ is rare and not paired. */
    override val quotePairs = mapOf('„' to '”', '“' to '”', '«' to '»', '(' to ')', '[' to ']')

    /** Lingua Libre had recordings of all twenty common words checked, Wiktionary of two. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * The breve of ă, the circumflex of â î and the comma (or the cedilla) of ș ț, dropped from every headword for its
     * second key; a headword written with a cedilla letter is keyed with the comma letter as well.
     */
    override val headwordMarks = HeadwordMarks(
        marks = "\u0306\u0302\u0326\u0327",
        bases = "aist",
        everyHeadword = true,
        replacements = mapOf("ş" to "ș", "ţ" to "ț", "Ş" to "Ș", "Ţ" to "Ț"),
    )

    override val inflectionRulesOnlyWithoutEntry = true

    /** Wiktionary links some Romanian words to their Moldovan Cyrillic spellings (este → есте, o → о). */
    override fun showsHeadword(expression: String): Boolean = expression.none { it in '\u0400'..'\u04FF' }

    /**
     * Pronouns and prepositions written joined to the next word with a hyphen: s-a, l-am, i-a, și-a, mi-a, ne-am,
     * într-o, dintr-un, printr-o, de-a; also without their marks (si-a, intr-o) and with a cedilla (şi-a). A word
     * written before such a pronoun (să-l, nu-i, dă-mi) keeps it as part of itself and is found by its start.
     */
    override val hyphenElisions = setOf(
        "s", "l", "m", "n", "v", "i", "te", "le", "ne", "mi", "ți", "ţi", "ti", "și", "şi", "si", "de", "într", "intr",
        "dintr", "printr",
    )

    /**
     * "dl." domnul, "dna." doamna, "nr." număr, "str." strada, "jud." județul, "sec." secolul (before a Roman numeral:
     * sec. XIV), "ed." editura, "ex." exemplu, "cca." circa; units and words that are words as well (loc. for
     * locuitori, "a avut loc.") are left out.
     */
    override val abbreviations = setOf(
        "dl", "dna", "dra", "dlui", "dnei", "dvs", "prof", "dr", "ing", "acad", "conf", "lect", "univ", "pr", "col",
        "lt", "cpt", "sf", "str", "bd", "bdul", "bl", "sc", "et", "ap", "nr", "pag", "pp", "art", "alin", "lit", "jud",
        "mun", "sec", "vol", "ed", "cf", "ex", "aprox", "cca", "tel", "mil", "mld", "pct", "vs", "a.c",
    )

    /**
     * Often a sentence's last word: "etc.", "ș.a." (și altele), "ș.a.m.d.", the eras "î.Hr." and "d.Hr.", "gen." (the
     * word gen as well as the general's rank), "p." (pagina, also an initial), "n." (născut), "v." (vezi).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "etc", "ș.a", "ş.a", "ș.a.m.d", "ş.a.m.d", "î.hr", "d.hr", "î.e.n", "e.n", "gen", "jr", "p", "n", "v",
    )
}
