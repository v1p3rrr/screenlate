package com.vpr.screenlate.core.common.language

/** Word rules shared by languages written with spaces between words. */
internal object SpacedWords {
    private const val WORD_CONNECTORS = "'’-"

    /** A word starts with a letter or digit; phrases and inflections are tried by the engine. */
    fun lookupStart(text: String): LookupStart? {
        if (text.isEmpty()) return null
        return if (Character.isLetterOrDigit(text.codePointAt(0))) LookupStart.Any else null
    }

    /** The whole word under the aim, as Yomitan's word scan resolution. */
    fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !Character.isLetterOrDigit(aimed.codePointAt(0))) return 0
        var index = before.length
        var count = 0
        while (index > 0) {
            val c = before[index - 1]
            val inWord = Character.isLetterOrDigit(c) || c == '́' ||
                (c in WORD_CONNECTORS && index > 1 && Character.isLetterOrDigit(before[index - 2]))
            if (!inWord) break
            index--
            count++
        }
        return count
    }
}
