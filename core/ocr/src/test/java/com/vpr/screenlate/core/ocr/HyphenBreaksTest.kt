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
    fun `the hyphen ml kit leaves out of a line's last word is put back, reaching the line's end`() {
        val boxes = listOf(Box(300f, 0f, 340f, 80f), Box(340f, 0f, 380f, 80f))
        val words = listOf(OcrWord("an", " ", Box(0f, 0f, 280f, 80f)), OcrWord("im", " ", Box(300f, 0f, 380f, 80f), boxes))
        val fixed = words.withLineEndHyphen("an im-", Box(0f, 0f, 400f, 80f), vertical = false)
        assertThat(fixed.map { it.text }).containsExactly("an", "im-").inOrder()
        assertThat(fixed.last().box).isEqualTo(Box(300f, 0f, 400f, 80f))
        assertThat(fixed.last().characterBoxes).containsExactly(boxes[0], boxes[1], Box(380f, 0f, 400f, 80f)).inOrder()
        // With ML Kit's separators the halves meet across the break as the lookup expects.
        val second = listOf(OcrWord("portant", " ", Box(0f, 100f, 300f, 180f)))
        val page = OcrPage(
            1000,
            1000,
            listOf(OcrParagraph(listOf(OcrLine(fixed, Box(0f, 0f, 400f, 80f), false), OcrLine(second, Box(0f, 100f, 300f, 180f), false)))),
            OcrEngineType.ML_KIT,
        )
        assertThat(page.paragraphText().trimEnd()).isEqualTo("an im- portant")
    }

    @Test
    fun `lines whose text has no hyphen of its own after a letter keep their words`() {
        val words = listOf(OcrWord("im", " ", Box(0f, 0f, 80f, 80f)))
        val box = Box(0f, 0f, 100f, 80f)
        assertThat(words.withLineEndHyphen("im", box, vertical = false)).isEqualTo(words)
        assertThat(words.withLineEndHyphen("im-", box, vertical = true)).isEqualTo(words)
        val hyphenated = listOf(OcrWord("im-", " ", Box(0f, 0f, 100f, 80f)))
        assertThat(hyphenated.withLineEndHyphen("im-", box, vertical = false)).isEqualTo(hyphenated)
        val digits = listOf(OcrWord("COVID-19", " ", Box(0f, 0f, 80f, 80f)))
        assertThat(digits.withLineEndHyphen("COVID-19-", box, vertical = false)).isEqualTo(digits)
        // No room left in the line: the hyphen gets a third of the word's height.
        val full = listOf(OcrWord("im", " ", Box(0f, 0f, 100f, 90f)))
        assertThat(full.withLineEndHyphen("im-", box, vertical = false).single().box).isEqualTo(Box(0f, 0f, 130f, 90f))
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
