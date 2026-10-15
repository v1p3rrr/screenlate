package com.vpr.screenlate.core.common.language

/**
 * The start of a lookup aimed inside a Latin word that the language converts to its own script (romaji): the word is
 * split from its first letter into the words the lookup finds, and the one under the aim is looked up, so each word of
 * "jidoujisho" (jidou, jisho) can be reached. Where nothing is found before the aim, or the aim lies past
 * [MAX_SEGMENTS] words, the lookup starts at the syllable under the aim.
 */
object ConvertedLatinAim {
    const val MAX_SEGMENTS = 8

    /**
     * @param word the text from the word's first letter on.
     * @param aimed the offset of the aimed letter in [word].
     * @param matchedAt the length in [word]'s characters of the match a lookup finds at an offset; 0 for none.
     * @return the offset in [word] where the lookup starts.
     */
    suspend fun start(support: LanguageSupport, word: String, aimed: Int, matchedAt: suspend (Int) -> Int): Int {
        var start = 0
        repeat(MAX_SEGMENTS) {
            val matched = matchedAt(start)
            if (matched <= 0) return syllableStart(support, word, aimed)
            if (start + matched > aimed) return start
            start += matched
        }
        return syllableStart(support, word, aimed)
    }

    /** The first letter of the converted syllable [aimed] belongs to: `sho` of "jisho" for its `h`. */
    fun syllableStart(support: LanguageSupport, word: String, aimed: Int): Int {
        val converted = support.fromLatin(MappedText.identity(word)) ?: return aimed
        var start = 0
        for (length in 1..converted.text.length) {
            val end = converted.sourceLength(length)
            if (end > aimed) break
            start = end
        }
        return start
    }
}
