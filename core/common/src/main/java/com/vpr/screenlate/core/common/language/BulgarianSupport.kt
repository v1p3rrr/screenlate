package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has only capitalization for Bulgarian; the language bundle adds Russian's stress mark removal, a grave stress
 * taken off words of three letters or more (кнѝга), a standalone й also looked up as the pronoun ѝ, which most text
 * writes so, and the app's own endings (articles, plurals, agreement, verb forms), whose results show only for a word
 * without an entry of its own. Wiktionary's forms carry their stress (кни́гата), which the import drops from the key.
 * Lingua Libre has recordings of the common words; Wiktionary's Bulgarian recordings are few.
 */
object BulgarianSupport : SpacedLanguageSupport() {
    override val language = Language.BULGARIAN
    override val glyph = "Б"
    override val codeLabel = "БГ"
    override val ocrScript = OcrScript.CYRILLIC
    override val wikidataId = "Q7918"
    override val languageTag = "bg"
    override val fontSample = "Жълтата дюля беше щастлива, че пухът, който цъфна, замръзна като гьон. Дадох ѝ кни́гата."
    override val translationSample = "Днес времето е хубаво. Ще отидем ли на разходка?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → [voˈda]"
    override val flagCountries = listOf("bg")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS
    override val fontSubsets = setOf("latin", "cyrillic")

    /** Without ѝ: PT Serif and PT Sans lack it and stay offered; the phone's font draws that letter. */
    override val fontLetters = "абвгдежзийклмнопрстуфхцчшщъьюя"

    /** „…“ outside, ‚…‘ inside, «…» as well; “ and ‘ close, so they open none. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '«' to '»', '(' to ')', '[' to ']')

    /**
     * Wiktionary's forms carry their stress as an acute (кни́гата, но́вият). Not the grave: ѝ is и with a grave, and the
     * pronoun ѝ "her" is no conjunction и. Its tables also tag positive forms "comparative" (мо́ята of мой), which only
     * the forms with по- and най- are.
     */
    override val headwordMarks =
        HeadwordMarks(marks = "́", bases = "аеиоуъюя", comparativePrefixes = listOf("по-", "най-"))

    /** The forms of words with tables are Wiktionary's own; the app's endings are for the words without them. */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "г." is година or господин, "гр." град, "с." село, "т." том, "в." вестник, "хил." хиляди, "лв." лева, "вж." виж
     * (see), "пр. н. е." and "сл. н. е." before and after the common era, joined or spaced (н and е stand with another
     * letter: е alone is "is"). "и т.н." and "и др." (and so on) usually end a sentence and are left out; "д-р" has no
     * period.
     */
    override val abbreviations = setOf(
        "г", "гр", "с", "т", "в", "ул", "бул", "пл", "обл", "проф", "доц", "акад", "инж", "т.е", "напр", "вж", "стр",
        "чл", "ал", "св", "хил", "млн", "млрд", "лв", "пр", "сл", "пр.н.е", "сл.н.е",
    )
    override val pairedLetters = setOf("н", "е")
}
