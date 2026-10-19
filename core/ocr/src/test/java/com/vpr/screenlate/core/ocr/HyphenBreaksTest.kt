package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class HyphenBreaksTest {

    /** A line of [words], each with the separator after it, as Lens gives them. */
    private fun line(index: Int, vararg words: Pair<String, String>): OcrLine {
        val top = index * 100f
        val ocrWords = words.mapIndexed { column, (text, separator) ->
            OcrWord(text, separator, Box(column * 300f, top, column * 300f + 280f, top + 80f))
        }
        return OcrLine(ocrWords, Box(0f, top, 1000f, top + 80f), vertical = false)
    }

    private fun page(vararg lines: OcrLine) = OcrPage(1000, 1000, listOf(OcrParagraph(lines.toList())), OcrEngineType.LENS)

    private fun OcrPage.paragraphText() = TextLayout(this).paragraphText(TextPosition(0, 0)).first

    @Test
    fun `a word hyphenated at a line end gets the line break that lens leaves out`() {
        val lens = page(line(0, "an" to " ", "impor-" to ""), line(1, "tant" to " ", "thing" to ""))
        assertThat(lens.paragraphText()).isEqualTo("an impor-tant thing")
        assertThat(lens.withHyphenBreaks(" ").paragraphText()).isEqualTo("an impor- tant thing")
        // The Unicode hyphen and the soft hyphen too.
        val unicode = page(line(0, "impor‐" to ""), line(1, "tant" to ""))
        assertThat(unicode.withHyphenBreaks(" ").paragraphText()).isEqualTo("impor‐ tant")
        val soft = page(line(0, "impor" + Char(0xAD) to ""), line(1, "tant" to ""))
        assertThat(soft.withHyphenBreaks(" ").paragraphText()).isEqualTo("impor" + Char(0xAD) + " tant")
    }

    @Test
    fun `other line ends, the last line and languages without spaces stay`() {
        // A dash of its own, a hyphen after a digit, a separator Lens gave, and a hyphen inside the line.
        val others = page(
            line(0, "this" to " ", "-" to ""),
            line(1, "COVID-19-" to ""),
            line(2, "Haus-" to " "),
            line(3, "well-known" to " ", "and" to ""),
            line(4, "last-" to ""),
        )
        assertThat(others.withHyphenBreaks(" ")).isEqualTo(others)
        val japanese = page(line(0, "大切-" to ""), line(1, "です" to ""))
        assertThat(japanese.withHyphenBreaks("")).isEqualTo(japanese)
    }
}
