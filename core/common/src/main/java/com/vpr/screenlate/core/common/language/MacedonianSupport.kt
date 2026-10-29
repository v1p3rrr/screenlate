package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has nothing for Macedonian; the language bundle borrows Bulgarian's capitalization and adds Russian's stress
 * mark removal, a grave stress taken off words of three letters or more, and the Latin è that most typed text writes
 * for ѐ (сè) read as ѐ. Wiktionary lists the forms of most words, so there are no ending rules. OCR reads Ѕ, Ј, Ѐ, Ѓ,
 * Ѝ as look-alikes, fixed letter for letter ([ocrWord]), and Љ as Л or ЛЬ and Ѕ as С, which a word from OCR that
 * finds nothing as read is tried with ([misreadSpellings]); Ќ and Ѓ read as К and Г are found through the marks folded
 * at import. Wiktionary recordings of Macedonian have romanized titles, so Lingua Libre's come first.
 */
object MacedonianSupport : SpacedLanguageSupport() {
    override val language = Language.MACEDONIAN
    override val glyph = "М"
    override val codeLabel = "МК"
    override val ocrScript = OcrScript.CYRILLIC
    override val wikidataId = "Q9296"
    override val languageTag = "mk"
    override val fontSample = "Ѓорѓи и Љубица ќе патуваат со џип, а ѕвездите светат над езерото. Њој сѐ уште ѝ се јавуваат."
    override val translationSample = "Денес времето е убаво. Ќе одиме ли на прошетка?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → [ˈvɔda]"
    override val flagCountries = listOf("mk")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS
    override val fontSubsets = setOf("latin", "cyrillic")

    /**
     * Without ѐ and ѝ: PT Serif and PT Sans lack both and stay offered, the phone's font draws them; Literata draws ѐ as
     * е with a combining grave.
     */
    override val fontLetters = "абвгдѓежзѕијклљмнњопрстќуфхцчџш"

    /** „…“ outside, ‚…‘ inside; “ and ‘ close, so they open none. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '(' to ')', '[' to ']')

    /** Wiktionary's forms carry their stress as an acute; the grave of ѐ and ѝ and the acute of ѓ and ќ stay. */
    override val headwordMarks = HeadwordMarks(marks = "́", bases = "аеиоу")

    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, MACEDONIAN_LOOK_ALIKES)

    /**
     * Latin S, J and È and the Cyrillic letters of other languages that recognition reads for Macedonian ones: Ѕ, Ј,
     * Ѐ (also as é and ё), Ѓ (as Ґ, Ғ and, in a serif font, Ї), Ѝ (as Й: й is no Macedonian letter).
     */
    private val MACEDONIAN_LOOK_ALIKES = mapOf(
        'S' to 'Ѕ', 's' to 'ѕ', 'J' to 'Ј', 'j' to 'ј', 'È' to 'Ѐ', 'è' to 'ѐ', 'É' to 'Ѐ', 'é' to 'ѐ', 'Ё' to 'Ѐ',
        'ё' to 'ѐ', 'Ґ' to 'Ѓ', 'ґ' to 'ѓ', 'Ғ' to 'Ѓ', 'ғ' to 'ѓ', 'Ї' to 'Ѓ', 'Й' to 'Ѝ', 'й' to 'ѝ',
    )

    /**
     * The first word with one of its first [MISREAD_PLACES] Ls as Љ, from the left, then one of its first Сs as Ѕ; ЛЬ is
     * Љ as well (ь is no Macedonian letter). лубов finds љубов, свезда ѕвезда; one place changes at a time.
     */
    override fun misreadSpellings(text: String): List<MappedText> {
        val word = text.takeWhile(Char::isLetter)
        fun misread(read: Char, meant: Char): List<MappedText> =
            word.indices.filter { word[it].lowercaseChar() == read }.take(MISREAD_PLACES).map { at ->
                val soft = read == 'л' && word.getOrNull(at + 1)?.lowercaseChar() == 'ь'
                val end = if (soft) at + 2 else at + 1
                val letter = if (word[at].isUpperCase()) meant.uppercaseChar() else meant
                val spelled = text.substring(0, at) + letter + text.substring(end)
                MappedText(spelled, IntArray(spelled.length) { if (it < at) it + 1 else it + end - at })
            }
        return misread('л', 'љ') + misread('с', 'ѕ')
    }

    /** large_mk writes ѐ as the Latin è (сè) and has Latin look-alikes in Cyrillic words (jас). */
    override fun frequencyWord(word: String): String = CyrillicLookAlikes.fix(word, MACEDONIAN_TYPED)

    private val MACEDONIAN_TYPED = mapOf('è' to 'ѐ', 'È' to 'Ѐ', 'j' to 'ј', 'J' to 'Ј', 's' to 'ѕ', 'S' to 'Ѕ')

    /**
     * "г." is година, "в." век, "т.е." and "т.н." that is and so-called, "нпр." на пример, "бр." број, "ден." денари,
     * "илј." илјади, "пр. н. е." and "н. е." the eras, joined or spaced (т, н and е stand with another letter: е alone
     * is "is"). "итн." and "и др." (and so on) usually end a sentence and are left out; "м-р" has no period.
     */
    override val abbreviations = setOf(
        "г", "в", "ул", "бул", "с", "проф", "доц", "акад", "т.е", "т.н", "нпр", "стр", "бр", "чл", "ст", "ден", "мил",
        "илј", "пр", "пр.н.е", "н.е", "св",
    )
    override val pairedLetters = setOf("т", "н", "е")

    /** Ls of a word from OCR tried as Љ, and Сs as Ѕ. */
    private const val MISREAD_PLACES = 3
}
