package com.vpr.screenlate.core.common.language

/**
 * Latin letters that look like the letters of [script]. OCR engines write them into words of that script (a Russian
 * word with a Latin "o", a Greek one with a Latin "T"), and such a word is found in no dictionary.
 */
internal class LookAlikes(private val script: Character.UnicodeScript, private val letters: Map<Char, Char>) {
    /**
     * [word] with its look-alikes in the script when it has a letter of the script or is a single letter: a word of one
     * letter (В, у, а, the Ukrainian і, the Greek article ο) has no other one to show its script. A longer word without
     * a letter of the script stays as read: an English word made of look-alikes (TOP, COM) must not become a Russian
     * one.
     *
     * @param own the language's look-alikes beyond these (Serbian ј for j).
     */
    fun fix(word: String, own: Map<Char, Char> = emptyMap()): String {
        if (word.none { it in letters || it in own }) return word
        if (word.none(::isScriptLetter) && word.count(Char::isLetter) != 1) return word
        return buildString(word.length) { word.forEach { append(own[it] ?: letters[it] ?: it) } }
    }

    /**
     * [word] with its look-alikes in the script also when it has no letter of the script, as long as all its letters
     * are look-alikes (Joj, je): for text known to be in the script. Other words as [fix] writes them.
     */
    fun fixInScript(word: String, own: Map<Char, Char> = emptyMap()): String {
        val letters = word.filter(Char::isLetter)
        if (letters.isEmpty() || !letters.all { it in this.letters || it in own }) return fix(word, own)
        return buildString(word.length) { word.forEach { append(own[it] ?: this@LookAlikes.letters[it] ?: it) } }
    }

    /** Whether [c] is a Latin letter that looks like one of the script's. */
    fun isLookAlike(c: Char): Boolean = c in letters

    /** The Latin letter that the script's letter [c] looks like, if any. */
    fun latinOf(c: Char): Char? = latin[c]

    private val latin = letters.entries.associate { (latin, own) -> own to latin }

    private fun isScriptLetter(c: Char) = c.isLetter() && Character.UnicodeScript.of(c.code) == script
}

internal val CyrillicLookAlikes = LookAlikes(
    Character.UnicodeScript.CYRILLIC,
    mapOf(
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
    ),
)

/** Capitals Greek shares with Latin, and the lowercase letters closest in shape (v for ν, n for η, c for ς). */
internal val GreekLookAlikes = LookAlikes(
    Character.UnicodeScript.GREEK,
    mapOf(
        'a' to '\u03b1',
        'c' to '\u03c2',
        'e' to '\u03b5',
        'i' to '\u03b9',
        'k' to '\u03ba',
        'n' to '\u03b7',
        'o' to '\u03bf',
        'p' to '\u03c1',
        't' to '\u03c4',
        'u' to '\u03c5',
        'v' to '\u03bd',
        'w' to '\u03c9',
        'x' to '\u03c7',
        'y' to '\u03b3',
        'A' to '\u0391',
        'B' to '\u0392',
        'E' to '\u0395',
        'H' to '\u0397',
        'I' to '\u0399',
        'K' to '\u039a',
        'M' to '\u039c',
        'N' to '\u039d',
        'O' to '\u039f',
        'P' to '\u03a1',
        'T' to '\u03a4',
        'X' to '\u03a7',
        'Y' to '\u03a5',
        'Z' to '\u0396',
    ),
)
