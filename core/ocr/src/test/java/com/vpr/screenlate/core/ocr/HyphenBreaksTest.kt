package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
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

    private fun OcrPage.inEnglish() = withLineEndSeparators(Language.ENGLISH.support)

    @Test
    fun `a word hyphenated at a line end gets the line break that lens leaves out`() {
        val lens = page(line(0, "an" to " ", "impor-" to ""), line(1, "tant" to " ", "thing" to ""))
        assertThat(lens.paragraphText()).isEqualTo("an impor-tant thing")
        assertThat(lens.inEnglish().paragraphText()).isEqualTo("an impor- tant thing")
        // The Unicode hyphen and the soft hyphen too.
        val unicode = page(line(0, "impor‐" to ""), line(1, "tant" to ""))
        assertThat(unicode.inEnglish().paragraphText()).isEqualTo("impor‐ tant")
        val soft = page(line(0, "impor" + Char(0xAD) to ""), line(1, "tant" to ""))
        assertThat(soft.inEnglish().paragraphText()).isEqualTo("impor" + Char(0xAD) + " tant")
        // Korean, whose other line ends stay as Lens gives them.
        val korean = page(line(0, "중요-" to ""), line(1, "하다" to ""))
        assertThat(korean.withLineEndSeparators(Language.KOREAN.support).paragraphText()).isEqualTo("중요- 하다")
    }

    @Test
    fun `a line end lens gives no separator gets the language's when the paragraph goes on`() {
        // ga-anki.png: "Cheannaigh sí" ending a line and "torthaí" starting the next came as "sítorthaí".
        val irish = page(line(0, "Cheannaigh" to " ", "sí" to ""), line(1, "torthaí" to " ", "úra." to ""))
        assertThat(irish.paragraphText()).isEqualTo("Cheannaigh sítorthaí úra.")
        assertThat(irish.withLineEndSeparators(Language.IRISH.support).paragraphText())
            .isEqualTo("Cheannaigh sí torthaí úra.")
        // A dash of its own and a hyphen after a digit too, as on-device recognition and photos read them.
        val dashes = page(line(0, "this" to " ", "-" to ""), line(1, "COVID-19-" to ""), line(2, "Impfung" to ""))
        assertThat(dashes.inEnglish().paragraphText()).isEqualTo("this - COVID-19- Impfung")
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
    fun `separators lens gave, the last line, korean line ends and languages without spaces stay`() {
        // A separator Lens gave, a hyphen inside the line with a separator after it, and the paragraph's last line.
        val others = page(
            line(0, "Haus-" to " "),
            line(1, "well-known" to " ", "and" to " "),
            line(2, "last" to ""),
        )
        assertThat(others.inEnglish()).isEqualTo(others)
        // Korean breaks a word at any syllable at a line end.
        val korean = page(line(0, "먹었" to ""), line(1, "습니다" to ""))
        assertThat(korean.withLineEndSeparators(Language.KOREAN.support)).isEqualTo(korean)
        val japanese = page(line(0, "大切-" to ""), line(1, "です" to ""))
        assertThat(japanese.withLineEndSeparators(Language.JAPANESE.support)).isEqualTo(japanese)
        val chinese = page(line(0, "重要" to ""), line(1, "的" to ""))
        assertThat(chinese.withLineEndSeparators(Language.CHINESE.support)).isEqualTo(chinese)
        val thai = page(line(0, "จริง" to ""), line(1, "ๆ" to ""))
        assertThat(thai.withLineEndSeparators(Language.THAI.support)).isEqualTo(thai)
    }
}
