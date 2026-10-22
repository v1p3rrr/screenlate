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

    fun swedish(word: String): String = replaceLetters(word, SWEDISH)

    fun danishNorwegian(word: String): String = replaceLetters(word, DANISH_NORWEGIAN)
}
