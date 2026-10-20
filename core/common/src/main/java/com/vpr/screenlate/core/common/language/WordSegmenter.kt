package com.vpr.screenlate.core.common.language

import java.util.Locale

/** Divides text into words where a language writes no spaces between them. */
fun interface WordSegmenter {
    /**
     * The UTF-16 indexes of [text] where its pieces (words, spaces, punctuation) start and end, ascending: 0 first and
     * the text's length last.
     */
    fun boundaries(text: String, locale: Locale): IntArray
}

/** Android's ICU, whose word break iterator divides Thai, Lao and Khmer with dictionaries. */
object IcuWordSegmenter : WordSegmenter {
    override fun boundaries(text: String, locale: Locale): IntArray {
        // android.jar's stubs return null in unit tests; the text stays one piece there.
        val iterator: android.icu.text.BreakIterator? = android.icu.text.BreakIterator.getWordInstance(locale)
        if (iterator == null || text.isEmpty()) return intArrayOf(0, text.length)
        iterator.setText(text)
        val result = ArrayList<Int>()
        var boundary = iterator.first()
        while (boundary != android.icu.text.BreakIterator.DONE) {
            result += boundary
            boundary = iterator.next()
        }
        return result.toIntArray()
    }
}
