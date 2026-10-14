package com.vpr.screenlate.core.common.language

import java.text.Normalizer

/**
 * Pinyin as typed into a search and as dictionaries give it: the letters that identify a reading and the tones on
 * them, so "nihao", "ni hao", "ni3hao3" and "nǐhǎo" all find 你好.
 */
internal object ChinesePinyin {

    /** The letters a to z of [reading] in lower case, ü as v, without tones and separators; null for other text. */
    fun key(reading: String): String? = letters(reading)?.let { letters -> String(CharArray(letters.size) { letters[it].letter }) }

    /**
     * See [LanguageSupport.readingMatch]; a reading without a mark on a typed tone's letter has the neutral tone. An
     * apostrophe typed before a syllable (xi'an) counts as well: pinyin writes one there, so a reading without a break
     * at that letter (xiān) does not match it.
     */
    fun match(query: String, reading: String): Int {
        val typed = letters(query) ?: return 0
        val given = letters(reading) ?: return 0
        if (typed.size != given.size) return 0
        var score = 0
        for (i in typed.indices) {
            val tone = typed[i].tone
            if (tone != UNMARKED) {
                val other = given[i].tone.takeUnless { it == UNMARKED } ?: NEUTRAL
                score += if (other == tone) 1 else -1
            }
            if (i > 0 && typed[i].apostropheBefore) score += if (given[i].breakBefore) 1 else -1
        }
        return score
    }

    private class Letter(var letter: Char, var tone: Int) {
        /** Whether a separator stands before the letter, and whether that is an apostrophe. */
        var breakBefore = false
        var apostropheBefore = false
    }

    /**
     * The letters of [text] with their tones: marks on the letters, or a number after a syllable (5 or 0 for the
     * neutral tone), which goes where the mark would ([placeTone]).
     */
    private fun letters(text: String): List<Letter>? {
        val letters = ArrayList<Letter>(text.length)
        var syllableStart = 0
        var separator: Char? = null

        fun accept(c: Char): Boolean {
            when {
                c in 'a'..'z' || c in 'A'..'Z' -> letters.add(
                    Letter(c.lowercaseChar(), UNMARKED).apply {
                        breakBefore = separator != null
                        apostropheBefore = separator?.let { it in APOSTROPHES } == true
                        separator = null
                    },
                )
                c in TONE_MARKS -> (letters.lastOrNull() ?: return false).tone = TONE_MARKS.getValue(c)
                c == DIAERESIS || c == ':' -> (letters.lastOrNull()?.takeIf { it.letter == 'u' } ?: return false).letter = 'v'
                c == CIRCUMFLEX -> return letters.lastOrNull()?.letter == 'e'
                c in '0'..'5' -> {
                    if (!placeTone(letters.subList(syllableStart, letters.size), c - '0')) return false
                    syllableStart = letters.size
                }
                c in SEPARATORS -> {
                    syllableStart = letters.size
                    separator = c
                }
                else -> return false
            }
            return true
        }

        for (c in text) {
            val decomposed = MARKED_LETTERS[c]
            if (decomposed == null) {
                if (!accept(c)) return null
            } else {
                for (part in decomposed) if (!accept(part)) return null
            }
        }
        return letters.takeIf { it.isNotEmpty() }
    }

    /** The tone [number] on the letter pinyin marks: a or e, else the o of ou, else the last vowel. */
    private fun placeTone(syllable: List<Letter>, number: Int): Boolean {
        if (syllable.isEmpty()) return false
        val indices = syllable.indices
        val target = indices.firstOrNull { syllable[it].letter == 'a' || syllable[it].letter == 'e' }
            ?: indices.firstOrNull { syllable[it].letter == 'o' && syllable.getOrNull(it + 1)?.letter == 'u' }
            ?: indices.lastOrNull { syllable[it].letter in VOWELS }
            ?: 0
        syllable[target].tone = if (number in 1..4) number else NEUTRAL
        return true
    }

    private const val UNMARKED = 0
    private const val NEUTRAL = 5
    private const val VOWELS = "aeiouv"

    /** Combining macron, acute, caron and grave: tones 1 to 4. */
    private val TONE_MARKS = mapOf(0x0304.toChar() to 1, 0x0301.toChar() to 2, 0x030C.toChar() to 3, 0x0300.toChar() to 4)
    private val DIAERESIS = 0x0308.toChar()
    private val CIRCUMFLEX = 0x0302.toChar()

    /** Letters of pinyin with their marks, decomposed once here rather than for every reading. */
    private val MARKED_LETTERS: Map<Char, String> = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüêếềḿńňǹĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛÜÊẾỀŃŇǸ"
        .associateWith { Normalizer.normalize(it.toString(), Normalizer.Form.NFD) }

    /** Between syllables or words: spaces, apostrophes (Xī'ān), hyphens, middle dots of names, commas of phrases. */
    private const val SEPARATORS = " '’-·‧,，"
    private const val APOSTROPHES = "'’"
}
