package com.vpr.screenlate.dictionary.api.languages

/**
 * Tells the language of a dictionary's headwords or definitions from the writing systems in a sample of them.
 * Cyrillic and Latin text go to [identify] (Android's on-device language detection), which may not know; Cyrillic then
 * counts as Russian and Latin as English when English function words are common in it.
 */
internal object LanguageGuess {

    /** A language code, or null when the sample is too small or mixed to tell. */
    fun of(text: String, identify: (String) -> String?): String? {
        val letters = Letters(text)
        if (letters.total < MIN_LETTERS) return null
        val share = { count: Int -> count.toFloat() / letters.total }
        return when {
            share(letters.cyrillic) >= FOREIGN_SHARE -> identify(letters.cyrillicText())?.takeIf { it in CYRILLIC } ?: "ru"
            share(letters.latin) >= FOREIGN_SHARE -> identify(letters.latinText()) ?: englishOrNull(letters.latinText())
            share(letters.hangul) >= FOREIGN_SHARE -> "ko"
            share(letters.kana + letters.han) >= JAPANESE_SHARE ->
                // Japanese text has kana in almost every sentence; Chinese has none.
                if (letters.kana < (letters.kana + letters.han) * MIN_KANA_SHARE) "zh" else "ja"
            else -> null
        }
    }

    private fun englishOrNull(latin: String): String? {
        val words = WORD.findAll(latin.lowercase()).map { it.value }.toList()
        if (words.isEmpty()) return null
        val function = words.count { it in ENGLISH_FUNCTION_WORDS }
        return "en".takeIf { function >= words.size * MIN_FUNCTION_SHARE }
    }

    /** Letters of a text counted by writing system; punctuation, digits and marks do not count. */
    private class Letters(private val text: String) {
        var kana = 0
        var han = 0
        var latin = 0
        var cyrillic = 0
        var hangul = 0
        var total = 0

        init {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)
                if (!Character.isLetter(codePoint)) continue
                total++
                when (Character.UnicodeScript.of(codePoint)) {
                    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
                    Character.UnicodeScript.HAN -> han++
                    Character.UnicodeScript.LATIN -> latin++
                    Character.UnicodeScript.CYRILLIC -> cyrillic++
                    Character.UnicodeScript.HANGUL -> hangul++
                    else -> Unit
                }
            }
        }

        fun latinText(): String = words(Character.UnicodeScript.LATIN)

        fun cyrillicText(): String = words(Character.UnicodeScript.CYRILLIC)

        /** The words of one script, space-separated, for language detection. */
        private fun words(script: Character.UnicodeScript): String = WORD.findAll(text)
            .map { it.value }
            .filter { word -> Character.UnicodeScript.of(word.codePointAt(0)) == script }
            .joinToString(" ")
            .take(MAX_DETECTION_CHARS)
    }

    private val WORD = Regex("""\p{L}+""")

    /** Less than this and a guess is noise. */
    private const val MIN_LETTERS = 40

    /** Definitions with this much Latin or Cyrillic are in that language, even with Japanese examples among them. */
    private const val FOREIGN_SHARE = 0.15f
    private const val JAPANESE_SHARE = 0.5f
    private const val MIN_KANA_SHARE = 0.05f
    private const val MIN_FUNCTION_SHARE = 0.05f
    private const val MAX_DETECTION_CHARS = 4000

    private val CYRILLIC = setOf("ru", "uk", "be", "bg", "sr", "mk", "kk", "mn")
    private val ENGLISH_FUNCTION_WORDS = setOf(
        "the", "a", "an", "of", "to", "in", "on", "for", "with", "by", "at", "from", "and", "or", "is", "be", "as",
        "something", "someone", "etc",
    )
}
