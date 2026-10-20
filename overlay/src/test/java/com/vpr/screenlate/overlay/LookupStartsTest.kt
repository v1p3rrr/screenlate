package com.vpr.screenlate.overlay

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.EnglishSupport
import com.vpr.screenlate.core.common.language.GermanSupport
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.ThaiRules
import com.vpr.screenlate.core.common.language.VietnameseSupport
import com.vpr.screenlate.core.common.language.WordSegmenter
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import org.junit.Test

class LookupStartsTest {

    /** One paragraph of [lines], each a line of words joined by the language's separator. */
    private fun layout(support: LanguageSupport, vararg lines: String): TextLayout {
        val separator = support.wordSeparator
        val ocrLines = lines.mapIndexed { index, line ->
            val top = index * 100f
            val words = (if (separator.isEmpty()) listOf(line) else line.split(separator)).mapIndexed { column, word ->
                OcrWord(word, separator, Box(column * 300f, top, column * 300f + 280f, top + 80f))
            }
            OcrLine(words, Box(0f, top, 1000f, top + 80f), vertical = false)
        }
        return TextLayout(OcrPage(1000, 1000, listOf(OcrParagraph(ocrLines)), OcrEngineType.LENS))
    }

    /** The layout offset of [part] in the paragraph's text, counted in characters (code points). */
    private fun TextLayout.at(part: String, plus: Int = 0): TextPosition {
        val text = paragraphs[0].joinToString("") { it.text }
        val index = text.indexOf(part)
        require(index >= 0) { part }
        return TextPosition(0, text.codePointCount(0, index) + plus)
    }

    private fun TextLayout.wordAt(aimed: TextPosition, support: LanguageSupport): String {
        val start = wordStart(this, aimed, support)
        return textFrom(start, aimed.offset - start.offset + 1)
    }

    @Test
    fun `a word aimed inside starts at its first letter in a spaced language`() {
        val text = layout(EnglishSupport, "the unhappy cat")
        assertThat(text.wordAt(text.at("unhappy", plus = 3), EnglishSupport)).isEqualTo("unha")
        assertThat(wordStart(text, text.at("cat"), EnglishSupport)).isEqualTo(text.at("cat"))
    }

    @Test
    fun `japanese starts at the aimed character`() {
        val text = layout(JapaneseSupport, "食べることができる")
        val aimed = text.at("ことが", plus = 1)
        assertThat(wordStart(text, aimed, JapaneseSupport)).isEqualTo(aimed)
        assertThat(earliestStart(text, aimed, JapaneseSupport)).isEqualTo(aimed)
    }

    @Test
    fun `characters are code points, as the language rules count them`() {
        // A letter outside the basic plane takes two UTF-16 units but one layout character.
        val text = layout(EnglishSupport, "x 𝒜bc")
        assertThat(wordStart(text, TextPosition(0, 4), EnglishSupport)).isEqualTo(TextPosition(0, 2))
    }

    @Test
    fun `a word longer than the lookback starts within it`() {
        val long = "a".repeat(WORD_LOOKBACK + 8)
        val text = layout(GermanSupport, "das $long")
        val last = text.at(long, plus = long.length - 1)
        assertThat(wordStart(text, last, GermanSupport)).isEqualTo(last.copy(offset = last.offset - WORD_LOOKBACK))
    }

    @Test
    fun `vietnamese looks up from the syllables before the aimed one, other languages from the word`() {
        val text = layout(VietnameseSupport, "Tôi là học sinh")
        val word = text.at("sinh")
        assertThat(earliestStart(text, word, VietnameseSupport)).isEqualTo(text.at("là"))
        val english = layout(EnglishSupport, "the school kid")
        assertThat(earliestStart(english, english.at("kid"), EnglishSupport)).isEqualTo(english.at("kid"))
    }

    /** Thai divided into [words], the longest first, as ICU's dictionary would divide it. */
    private class DictionaryThai(vararg words: String) : ThaiRules(
        WordSegmenter { text, _ ->
            val boundaries = mutableListOf(0)
            var index = 0
            while (index < text.length) {
                index += words.filter { text.startsWith(it, index) }.maxOfOrNull { it.length } ?: 1
                boundaries += index
            }
            boundaries.toIntArray()
        },
    )

    @Test
    fun `thai looks up from the segmenter's word under the aim and the two before it`() {
        val thai = DictionaryThai("ห้อง", "สมุด", "เปิด", "เช้า")
        val text = layout(thai, "ห้องสมุดเปิดเช้า")
        // The text after the aim tells which word the aimed letter is in.
        assertThat(text.wordAt(text.at("สมุด", plus = 2), thai)).isEqualTo("สมุ")
        assertThat(text.wordAt(text.at("เปิด", plus = 1), thai)).isEqualTo("เป")
        val word = text.at("เช้า")
        assertThat(earliestStart(text, word, thai)).isEqualTo(text.at("สมุด"))
        assertThat(earliestStart(text, text.at("สมุด"), thai)).isEqualTo(text.at("ห้อง"))
    }

    @Test
    fun `the second half of a word hyphenated at a line end looks up from the first half as well`() {
        val text = layout(EnglishSupport, "an impor-", "tant thing")
        val aimed = text.at("tant", plus = 2)
        val word = wordStart(text, aimed, EnglishSupport)
        assertThat(word).isEqualTo(text.at("tant"))
        assertThat(earliestStart(text, word, EnglishSupport)).isEqualTo(text.at("impor"))
        val japanese = layout(JapaneseSupport, "大切-", "です")
        assertThat(earliestStart(japanese, japanese.at("です"), JapaneseSupport)).isEqualTo(japanese.at("です"))
    }
}
