package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Dictionaries give a Russian word's stress in its reading (кошка read as ко́шка), which the lookup page shows in the
 * word's place; text written with stress marks or with е for ё is matched by the dictionary engine's text processors.
 * Wiktionary recordings of Russian name no regions, so there are no audio regions.
 */
object RussianSupport : SpacedLanguageSupport() {
    override val language = Language.RUSSIAN
    override val glyph = "Я"
    override val ocrScript = OcrScript.CYRILLIC
    override val iso639Part3 = "rus"
    override val wikidataId = "Q7737"
    override val languageTag = "ru"
    override val fontSample = "Съешь же ещё этих мягких французских булок, да выпей чаю. Ударе́ние, ёж."
    override val translationSample = "Сегодня хорошая погода. Пойдём гулять?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → [vɐˈda]"
    override val flagCountries = listOf("ru")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS
    override val fontSubsets = setOf("latin", "cyrillic")

    /** Wiktionary has recordings of more Russian words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** «…» outside, „…“ inside; “ closes the inner quotes, so it opens none. */
    override val quotePairs = mapOf('«' to '»', '„' to '“', '(' to ')', '[' to ']')
    override val furiganaMarkers = true

    /** Wiktionary's forms name their lemmas stressed (почти → почти́ть), some in Latin transcription (oná). */
    override val headwordMarks = HeadwordMarks(marks = "\u0301\u0300", bases = "аеиоуыэюяaeiouy")

    /**
     * "г" and "в" are год and век ("в 1999 г.", "XX в."); "т", "е", "к" and "н" make up "т. е.", "т. к." and "т. н.".
     * Abbreviations that usually end a sentence or are also words are left out: "и т. д.", "и т. п.", "и др.", им (them),
     * ок, units and currencies.
     */
    override val abbreviations = setOf(
        "т.е", "т.к", "т.н", "т.о", "т", "е", "к", "н", "напр", "см", "ср", "г", "гг", "в", "вв", "с", "стр", "рис",
        "табл", "гл", "ул", "пр", "кв", "обл", "пос", "ст", "проф", "акад", "доц", "св", "гр", "тов", "тыс", "англ",
        "лат", "фр", "нем", "греч", "ред", "изд", "вып",
    )
}
