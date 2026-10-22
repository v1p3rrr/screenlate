package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has nothing for Kazakh; the language bundle borrows Mongolian's capitalization and adds the app's ending
 * rules and Latin i, I, h, ə written for і, І, һ, ә inside a Cyrillic word, in any text: Kazakh keyboards are often
 * missing, and the letters look alike. Headwords are also found with the Kazakh letters written as their Russian twins
 * (қ к, ғ г, ң н, ұ ү у, ө о, ә а, і и, һ х), which OCR and a Russian keyboard give ([headwordMarks]); the lookup text
 * keeps its letters, so a Kazakh letter written in a word finds only the spellings with it ("оң" finds оң, "он" finds
 * он, оң and өң).
 */
object KazakhSupport : TurkicCyrillicSupport() {
    override val language = Language.KAZAKH
    override val glyph = "Қ"
    override val wikidataId = "Q9252"
    override val languageTag = "kk"
    override val fontSample = "Әжем шаһардағы үлкен көпірдің қасында ұзақ жүрді."
    override val translationSample = "Бүгін ауа райы жақсы. Серуенге шығамыз ба?"
    override val audioTestWord = "кітап"
    override val pronunciationSample = "күн → [kʰʏn]"
    override val flagCountries = listOf("kz")
    override val fontLetters = "аәбвгғдеёжзийкқлмнңоөпрстуұүфхһцчшщъыіьэюя"

    /** Kazakh letters by the Russian ones that stand for them where a keyboard or OCR lacks them. */
    private val RUSSIAN_TWINS = mapOf(
        "қ" to "к", "ғ" to "г", "ң" to "н", "ұ" to "у", "ү" to "у", "ө" to "о", "ә" to "а", "і" to "и", "һ" to "х",
    )

    override val headwordMarks = HeadwordMarks(
        marks = "",
        bases = "",
        everyHeadword = true,
        replacements = RUSSIAN_TWINS + RUSSIAN_TWINS.entries.associate { (letter, twin) -> letter.uppercase() to twin.uppercase() },
    )

    /** Latin look-alikes in OCR's words, i, h and ə among them, written as the Cyrillic letters. */
    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, KAZAKH_LOOK_ALIKES)

    /** The frequency list writes words as typed: Latin i, I, h, ə inside Cyrillic ones are the Kazakh letters. */
    override fun frequencyWord(word: String): String = kazakhLetters(word)

    /**
     * "ж." and "жж." are жыл and жылдар (year, years: "2007 ж."), "ғ." and "ғғ." ғасыр (century), "б.з.д." and "б.з.б."
     * before and in our era, "т.б." and "т.с.с." and so on, "мыс." мысалы (for example), "қ." қаласы (city), "көш."
     * көшесі (street), "обл." облысы, "ауд." ауданы, "шам." шамамен (about), "ағыл." and "орыс." English and Russian.
     * Each also ends sentences, so a period after one ends nothing only before a lowercase letter or a digit.
     */
    override val abbreviationsBeforeLowercase = setOf(
        "ж", "жж", "ғ", "ғғ", "б.з.д", "б.з.б", "т.б", "т.с.с", "мыс", "млн", "млрд", "қ", "шам", "көш", "обл", "ауд",
        "ағыл", "орыс",
    )

    /** Latin letters that stand for Kazakh ones: i І, h һ, and the schwa ə Ə. */
    private val KAZAKH_LOOK_ALIKES = mapOf(
        'i' to 'і', 'I' to 'І', 'h' to 'һ', '\u0259' to '\u04D9', '\u018F' to '\u04D8',
    )

    /** [word] with the Latin letters of [KAZAKH_LOOK_ALIKES] as Kazakh ones when it has a Cyrillic letter. */
    internal fun kazakhLetters(word: String): String {
        if (word.none { it in KAZAKH_LOOK_ALIKES } || word.none { it in '\u0400'..'\u04FF' }) return word
        return buildString(word.length) { word.forEach { append(KAZAKH_LOOK_ALIKES[it] ?: it) } }
    }
}
