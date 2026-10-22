package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Wiktionary writes Lithuanian forms with stress marks (žmogaũs, a form of žmogùs) and its lemma rows without them, so
 * the import keys the forms without them and stores the lemma names so ([headwordMarks]). Every headword is also keyed
 * without the marks of ą č ę ė į š ų ū ž, so text typed or read without them finds the words with them, the exact
 * spelling first. Ordinals are written with an ending (5-asis), not a period.
 */
object LithuanianSupport : SpacedLanguageSupport() {
    override val language = Language.LITHUANIAN
    override val glyph = "Ė"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9083"
    override val languageTag = "lt"
    override val fontSample = "Įlinkdama fechtuotojo špaga sublykčiojusi pragręžė apvalų arbūzą."
    override val translationSample = "Šiandien graži diena. Gal eime pasivaikščioti?"
    override val audioTestWord = "vanduo"
    override val pronunciationSample = "vanduo → [ʋɐnˈdʊɔ]"
    override val flagCountries = listOf("lt")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "ąčęėįšųūž"

    /** „…“ closes with the character English opens with; „…” and English quotes are rare and not paired. */
    override val quotePairs = mapOf('„' to '“', '(' to ')', '[' to ']')

    /** Lingua Libre had recordings of three of twenty common words checked, Wiktionary of none. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * The acute, grave and tilde of stressed forms (žmogaũs, Angòlos) and the dot over an accented i (bi̇̀tė), not the
     * one of ė; then, for every headword's further key, the ogonek of ą ę į ų, the dot of ė, the macron of ū and the
     * caron of č š ž.
     */
    override val headwordMarks = HeadwordMarks(
        marks = "\u0301\u0300\u0303",
        bases = "aeiouylmnr",
        everyHeadword = true,
        lemmasAsWritten = false,
        markBases = mapOf("\u0307" to "i"),
        letterMarks = "\u0328\u0307\u0304\u030C",
        letterBases = "aeiucsz",
    )

    /**
     * "pvz." (pavyzdžiui), "t.y." (tai yra), titles before a name (prof., dr., doc., kun., šv., gen.), the language
     * labels before a foreign word (angl., lot., vok.), "žr." and "plg." (see, compare), "Nr." before a number.
     */
    override val abbreviations = setOf(
        "pvz", "t.y", "prof", "dr", "doc", "kun", "šv", "gen", "plk", "kpt", "ltn", "mjr", "inž", "vyr", "mok", "aut",
        "sud", "vad", "gim", "žr", "plg", "nr", "el", "š.m", "angl", "lot", "vok", "rus", "lenk", "liet", "pranc",
    )

    /**
     * "t. y.", "ir t. t.", "š. m." (šių metų) and "pr. m. e." (prieš mūsų erą) written with spaces: the letter takes its
     * period from the one beside it.
     */
    override val pairedLetters = setOf("t", "y", "e", "š")

    /**
     * Often a sentence's last word: "m." (metai, after a year, or metrai), "a." (amžius), "d." (diena), "g." (gatvė,
     * gramai), "p." (puslapis), "r." (rajonas), units and amounts (proc., tūkst., mln., val., min.), "ir kt.", "ir
     * pan."; a single capital letter is an initial (A. Veryga), as in other languages.
     */
    override val abbreviationsBeforeLowercase = setOf(
        "m", "a", "d", "g", "p", "r", "mln", "mlrd", "tūkst", "proc", "val", "min", "sek", "kv", "gyv", "vnt", "mėn",
        "pan", "kt", "t.t", "sav", "sen", "raj", "apskr", "pab", "psl", "red", "tel", "st", "pr",
    )

    /**
     * ML Kit reads Ū ū ė į as Ü ü é ị, which Lithuanian does not write; text read from apps is taken as written, and a
     * foreign name read by OCR loses its é (Josė).
     */
    override fun ocrWord(text: String): String = replaceLetters(text, OCR_LETTERS)

    private val OCR_LETTERS = mapOf('Ü' to 'Ū', 'ü' to 'ū', 'é' to 'ė', 'ị' to 'į')
}
