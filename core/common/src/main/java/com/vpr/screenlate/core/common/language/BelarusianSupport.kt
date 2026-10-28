package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has only capitalization for Belarusian; the language bundle adds Ukrainian's stress mark removal and
 * apostrophe variants (сям'я, сям’я). Wiktionary's forms carry their stress (па́льца), which the import drops from the
 * key. There is no frequency list. Wiktionary recordings of Belarusian name no regions (`Be-вада.ogg`).
 */
object BelarusianSupport : SpacedLanguageSupport() {
    override val language = Language.BELARUSIAN
    override val glyph = "Б"
    override val codeLabel = "БЕ"
    override val ocrScript = OcrScript.CYRILLIC
    override val wikidataId = "Q9091"
    override val languageTag = "be"
    override val fontSample = "У Іўі худы жвавы чорт у зялёнай камізэльцы пабег пад'есці фаршыраваных яек."
    override val translationSample = "Сёння добрае надвор'е. Пойдзем гуляць?"
    override val audioTestWord = "вада"
    override val pronunciationSample = "вада → [vaˈda]"
    override val flagCountries = listOf("by")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS
    override val fontSubsets = setOf("latin", "cyrillic")
    override val fontLetters = "абвгдеёжзійклмнопрстуўфхцчшыьэюя"

    /** Wiktionary has recordings of more Belarusian words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** «…» outside, „…“ inside; “ closes the inner quotes, so it opens none. */
    override val quotePairs = mapOf('«' to '»', '„' to '“', '(' to ')', '[' to ']')

    /** Wiktionary's forms carry their stress (па́льца, ко́шкі), which plain text does not. */
    override val headwordMarks = HeadwordMarks(marks = "́̀", bases = "аеёіоуыэюя")

    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, BELARUSIAN_LOOK_ALIKES)

    /** Latin i looks like the Belarusian і, and ŭ (u with breve) like ў. */
    private val BELARUSIAN_LOOK_ALIKES = mapOf('i' to 'і', 'I' to 'І', 'ŭ' to 'ў', 'Ŭ' to 'Ў')

    /**
     * "г." and "гг." are год and гады ("у 1991 г."), "ст." стагоддзе or старонка, "с." старонка, "в." вёска, "м." места,
     * "г." and "зн." make up "г. зн." (that is). "і г. д." and "і інш." (and so on) usually end a sentence and are left
     * out, as are units and currencies.
     */
    override val abbreviations = setOf(
        "напр", "г", "гг", "ст", "с", "в", "м", "вул", "пл", "вобл", "тыс", "праф", "акад", "дац", "св", "рыс", "табл",
        "англ", "лац", "ням", "фр", "зн",
    )
}
