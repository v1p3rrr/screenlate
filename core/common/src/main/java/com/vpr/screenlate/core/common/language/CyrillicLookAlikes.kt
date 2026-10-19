package com.vpr.screenlate.core.common.language

/**
 * Latin letters that look like Cyrillic ones. OCR engines write them into Cyrillic words (a Russian word with a Latin
 * "o"), and such a word is found in no dictionary.
 */
internal object CyrillicLookAlikes {
    private val cyrillic = mapOf(
        'a' to '\u0430',
        'c' to '\u0441',
        'e' to '\u0435',
        'o' to '\u043e',
        'p' to '\u0440',
        'x' to '\u0445',
        'y' to '\u0443',
        'A' to '\u0410',
        'B' to '\u0412',
        'C' to '\u0421',
        'E' to '\u0415',
        'H' to '\u041d',
        'K' to '\u041a',
        'M' to '\u041c',
        'O' to '\u041e',
        'P' to '\u0420',
        'T' to '\u0422',
        'X' to '\u0425',
    )

    /**
     * [word] with its look-alikes in Cyrillic when it has a Cyrillic letter. A word without one stays as read: an
     * English word made of look-alikes (TOP, COM) must not become a Russian one.
     *
     * @param own the language's look-alikes beyond these (Serbian ј for j).
     */
    fun fix(word: String, own: Map<Char, Char> = emptyMap()): String {
        if (word.none { it in cyrillic || it in own } || word.none(::isCyrillicLetter)) return word
        return buildString(word.length) { word.forEach { append(own[it] ?: cyrillic[it] ?: it) } }
    }

    private fun isCyrillicLetter(c: Char) = c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.CYRILLIC
}
