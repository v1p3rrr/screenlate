package com.vpr.screenlate.core.common.language

/**
 * The letters on-device recognition reads for å in Swedish, Danish and Norwegian, none of which writes them: ML Kit's
 * Latin model gives ả or â on clean screens and ä on busy ones. One character for one, so character boxes stay valid.
 * Only OCR text gets them back ([LanguageSupport.ocrWord]); text an app gives keeps its letters.
 */
internal object ScandinavianLetters {
    private val SWEDISH = mapOf('ả' to 'å', 'â' to 'å', 'Ả' to 'Å', 'Â' to 'Å')

    /** Danish and Norwegian write no ä either; Swedish does. */
    private val DANISH_NORWEGIAN = SWEDISH + mapOf('ä' to 'å', 'Ä' to 'Å')

    fun swedish(word: String): String = replace(word, SWEDISH)

    fun danishNorwegian(word: String): String = replace(word, DANISH_NORWEGIAN)

    private fun replace(word: String, letters: Map<Char, Char>): String =
        if (word.none { it in letters }) word else String(CharArray(word.length) { letters[word[it]] ?: word[it] })
}
