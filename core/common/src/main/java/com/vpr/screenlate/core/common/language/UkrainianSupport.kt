package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan's Ukrainian code deinflects (35 described rules), drops stress marks from the text and matches the
 * apostrophe variants (п'ять, п’ять, пʼять). Dictionaries give lemmas no reading; forms carry their stress, which the
 * import drops from the key. Wiktionary recordings of Ukrainian name no regions (`Uk-кіт.ogg`), so there are no audio
 * regions.
 */
object UkrainianSupport : SpacedLanguageSupport() {
    override val language = Language.UKRAINIAN
    override val glyph = "Ї"
    override val ocrScript = OcrScript.CYRILLIC
    override val iso639Part3 = "ukr"
    override val wikidataId = "Q8798"
    override val languageTag = "uk"
    override val fontSample = "Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів! Наго́лос, пʼять."
    override val translationSample = "Сьогодні гарна погода. Підемо гуляти?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → [woˈdɑ]"
    override val flagCountries = listOf("ua")

    /** The apostrophe ʼ (U+02BC) is a modifier letter, outside the Latin and Cyrillic blocks. */
    override val systemFonts = CYRILLIC_SYSTEM_FONTS.copy(unicodeRange = CYRILLIC_SYSTEM_FONTS.unicodeRange + ", U+02BC")
    override val fontSubsets = setOf("latin", "cyrillic")

    /** Wiktionary has recordings of common words; Lingua Libre's are mostly rarer words and phrases. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** «…» outside, „…“ inside; “ closes the inner quotes, so it opens none. */
    override val quotePairs = mapOf('«' to '»', '„' to '“', '(' to ')', '[' to ']')

    /** Wiktionary's forms carry their stress (чита́ю), a few a secondary one as well (а̀втостради). */
    override val headwordMarks = HeadwordMarks(marks = "́̀", bases = "аеєиіїоуюя")

    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, UKRAINIAN_LOOK_ALIKES)

    /** wordfreq writes the apostrophe as ' (п'ять), some dictionaries as ’ or ʼ. */
    override fun frequencySpellings(word: String): List<String> =
        if ('\'' in word) listOf(word.replace('\'', '’'), word.replace('\'', 'ʼ')) else emptyList()

    /** Latin i and ï look like the Ukrainian і and ї. */
    private val UKRAINIAN_LOOK_ALIKES = mapOf('i' to 'і', 'I' to 'І', 'ï' to 'ї', 'Ï' to 'Ї')

    /**
     * "р." and "рр." are рік and роки ("у 1991 р."), "ст." століття or стаття, "с." сторінка or село, "м." місто, "т." and
     * "зв." make up "т. зв.", "т." and "ч." "у т. ч.". "і т. д." and "і т. ін." (and so on) usually end a sentence and
     * are left out, as are units and currencies.
     */
    override val abbreviations = setOf(
        "напр", "див", "пор", "р", "рр", "ст", "с", "м", "вул", "просп", "пл", "обл", "тис", "проф", "акад", "доц",
        "ім", "св", "рис", "табл", "англ", "лат", "нім", "франц", "т", "зв", "ч",
    )
}
