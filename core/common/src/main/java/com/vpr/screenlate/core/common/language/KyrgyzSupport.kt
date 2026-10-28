package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has nothing for Kyrgyz; the language bundle borrows Mongolian's capitalization and adds the app's ending
 * rules in the four-way vowel harmony, the question particle written onto the word among them (барасыңбы). Headwords
 * are also found with the Kyrgyz letters written as their Russian twins (ң н, ө о, ү у), which OCR and a Russian
 * keyboard give ([headwordMarks]); as in Kazakh, a Kyrgyz letter written in a word finds only the spellings with it.
 */
object KyrgyzSupport : TurkicCyrillicSupport() {
    override val language = Language.KYRGYZ
    override val glyph = "К"
    override val codeLabel = "КЫ"
    override val wikidataId = "Q9255"
    override val languageTag = "ky"
    override val fontSample = "Бүгүн көчөдө жаңы дүкөн ачылды, анда өрүк жана жүзүм сатылат."
    override val translationSample = "Бүгүн аба ырайы жакшы. Сейилдөөгө чыгабызбы?"
    override val audioTestWord = "китеп"
    override val pronunciationSample = "китеп → [kiˈtep]"
    override val flagCountries = listOf("kg")
    override val fontLetters = "абвгдеёжзийклмнңоөпрстуүфхцчшщъыьэюя"

    /** Kyrgyz letters by the Russian ones that stand for them where a keyboard or OCR lacks them. */
    private val RUSSIAN_TWINS = mapOf("ң" to "н", "ө" to "о", "ү" to "у")

    override val headwordMarks = HeadwordMarks(
        marks = "",
        bases = "",
        everyHeadword = true,
        replacements = RUSSIAN_TWINS + RUSSIAN_TWINS.entries.associate { (letter, twin) -> letter.uppercase() to twin.uppercase() },
    )

    /** Latin look-alikes in OCR's words, the barred o among them, written as the Cyrillic letters. */
    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, KYRGYZ_LOOK_ALIKES)

    /**
     * "ж. б." жана башка (and so on), "б. з. ч." биздин заманга чейин (before our era), "ж." and "жж." жыл and жылдар
     * (year, years: "1991-ж."), "к." and "кк." кылым (century), "б.а." башкача айтканда (in other words), "мис." мисалы
     * (for example), "обл." облусу, "түн." and "түш." түндүк and түштүк (north, south). Each also ends sentences, so a
     * period after one ends nothing only before a lowercase letter or a digit.
     */
    override val abbreviationsBeforeLowercase = setOf(
        "ж", "б", "з", "ч", "жж", "к", "кк", "б.а", "мис", "обл", "түн", "түш", "млн", "млрд",
    )

    /** "проф." stands before a name and ends no sentence. */
    override val abbreviations = setOf("проф")

    /** The barred o, which OCR may give for ө. */
    private val KYRGYZ_LOOK_ALIKES = mapOf('\u0275' to '\u04E9', '\u019F' to '\u04E8')
}
