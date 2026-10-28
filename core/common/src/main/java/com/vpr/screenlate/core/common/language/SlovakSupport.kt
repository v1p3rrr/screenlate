package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * The import keys every headword also without the marks of á ä č ď é í ĺ ľ ň ó ô ŕ š ť ú ý ž ([headwordMarks]), so
 * text typed or read without them finds the words with them (robit → robiť), the exact spelling first. Wiktionary's
 * Slovak dictionaries lack many common words (v, za, už, však), so text they find nothing for is looked up in the Czech
 * dictionaries the user has ([fallbackLanguage]).
 */
object SlovakSupport : SpacedLanguageSupport() {
    override val language = Language.SLOVAK
    override val glyph = "S"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9058"
    override val languageTag = "sk"
    override val fontSample = "Kŕdeľ šťastných ďatľov učí pri ústí Váhu mĺkveho koňa obhrýzať kôru a žrať čerstvé mäso."
    override val translationSample = "Dnes je pekné počasie. Pôjdeme na prechádzku?"
    override val audioTestWord = "voda"
    override val pronunciationSample = "voda → [ˈvɔda]"
    override val flagCountries = listOf("sk")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "áäčďéíĺľňóôŕšťúýž"

    /** „…“ and ‚…‘ close with the characters English opens with. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '(' to ')', '[' to ']')

    /** Lingua Libre has recordings of 19 of 20 common words checked, Wiktionary of one. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Ordinals in digits take a period: "20. storočie", "1. januára". */
    override val ordinalPeriod = true

    /** The acute of á é í ĺ ó ŕ ú ý, the caron of č ď ľ ň š ť ž, the circumflex of ô and the diaeresis of ä. */
    override val headwordMarks = HeadwordMarks(
        marks = "\u0301",
        bases = "aeiouylr",
        everyHeadword = true,
        markBases = mapOf("\u030C" to "cdlnstz", "\u0302" to "o", "\u0308" to "a"),
    )

    override val fallbackLanguage = Language.CZECH

    /**
     * "napr." (napríklad), "tzv." (takzvaný), "t.j." (to jest), "resp.", "č." (číslo) and "s." (strana) before a
     * number, titles before a name (Ing., Mgr., prof., sv. for svätý), the language labels before a foreign word (angl.,
     * lat., nem.); "n.l." and "pr.n.l." are the era written without spaces. Words that are words as well are left out:
     * nám (námestie, and "us"), bod, pol.
     */
    override val abbreviations = setOf(
        "napr", "tzv", "tzn", "t.j", "tj", "resp", "príp", "popr", "č", "s", "str", "ul", "tel", "ing", "mgr", "bc",
        "phdr", "mudr", "judr", "rndr", "paeddr", "mvdr", "prof", "doc", "dr", "sv", "gen", "plk", "mjr", "kpt", "vs",
        "spol", "angl", "lat", "nem", "maď", "rus", "fr", "gr", "slov", "čes", "tal", "n.l", "pr.n.l",
    )

    /** The letters of "t. j.", "n. l." and "pr. n. l." written with spaces. */
    override val pairedLetters = setOf("t", "j", "n", "l")

    /**
     * Often a sentence's last word: "r." (rok, after a year), "st." and "stor." (storočie), "pred Kr.", "atď.", "a
     * pod.", units and amounts (hod., min., mil., mld., tis.), company forms (a.s., s.r.o.), "p." (pán, strana), "pr."
     * of "pr. n. l.".
     */
    override val abbreviationsBeforeLowercase = setOf(
        "r", "st", "stor", "kr", "atď", "pod", "hod", "min", "max", "sek", "mil", "mld", "tis", "cca", "a.s", "s.r.o",
        "roč", "čl", "ods", "písm", "zb", "p", "pí", "pr",
    )
}
