package com.vpr.screenlate.core.common.language

import java.util.Locale

/** ICU4J 66, the ICU of Android 11, in place of `android.icu`, which android.jar's stubs leave empty in unit tests. */
object Icu4jSegmenter : WordSegmenter {
    override fun boundaries(text: String, locale: Locale): IntArray {
        val iterator = com.ibm.icu.text.BreakIterator.getWordInstance(locale)
        iterator.setText(text)
        val result = ArrayList<Int>()
        var boundary = iterator.first()
        while (boundary != com.ibm.icu.text.BreakIterator.DONE) {
            result += boundary
            boundary = iterator.next()
        }
        return result.toIntArray()
    }
}

/** Thai segmented by [Icu4jSegmenter], as on a phone. */
object IcuThai : ThaiRules(Icu4jSegmenter)

/** Khmer segmented by [Icu4jSegmenter], as on a phone. */
object IcuKhmer : KhmerRules(Icu4jSegmenter)
