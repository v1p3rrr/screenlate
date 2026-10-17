package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Test

class RightToLeftTest {
    /** A word as the recognizer reads it, its characters 10 px wide from [left]. */
    private fun word(text: String, left: Float, separator: String = " "): OcrWord {
        val boxes = text.indices.map { Box(left + it * 10f, 0f, left + it * 10f + 10f, 20f) }
        return OcrWord(text, separator, Box.unionOf(boxes)!!, boxes)
    }

    private fun page(vararg words: OcrWord) = OcrPage(
        200,
        20,
        listOf(OcrParagraph(listOf(OcrLine(words.toList(), Box(0f, 0f, 200f, 20f), vertical = false)))),
        OcrEngineType.DEVICE_MODEL,
    )

    private fun OcrPage.words() = paragraphs.single().lines.single().words

    @Test
    fun `words and their letters are turned into reading order`() {
        // "\u0633\u0644\u0627\u0645 \u0639\u0644\u064a\u0643\u0645" read from the left: the second word first, each word backwards.
        val read = page(word("\u0645\u0643\u064a\u0644\u0639", 0f), word("\u0645\u0627\u0644\u0633", 60f, separator = ""))

        val words = readingOrder(read).words()

        assertThat(words.map { it.text }).containsExactly("\u0633\u0644\u0627\u0645", "\u0639\u0644\u064a\u0643\u0645").inOrder()
        assertThat(words.map { it.separator }).containsExactly(" ", "").inOrder()
        assertThat(words[0].characterBoxes!!.map { it.left }).containsExactly(90f, 80f, 70f, 60f).inOrder()
        assertThat(words[0].box).isEqualTo(Box(60f, 0f, 100f, 20f))
        assertThat(readingOrder(read).paragraphs.single().lines.single().rightToLeft).isTrue()
    }

    @Test
    fun `Latin letters and digits keep their order`() {
        val read = page(word("ABC", 0f), word("12.5", 40f), word("\u0645\u0627\u0644\u0633", 90f, separator = ""))

        val words = readingOrder(read).words()

        assertThat(words.map { it.text }).containsExactly("\u0633\u0644\u0627\u0645", "ABC", "12.5").inOrder()
        assertThat(words.map { it.separator }).containsExactly(" ", " ", "").inOrder()
        assertThat(words[1].box).isEqualTo(Box(0f, 0f, 30f, 20f))
    }

    @Test
    fun `a word without one box per character keeps its box`() {
        val read = page(OcrWord("\u0645\u0627\u0644\u0633", "", Box(0f, 0f, 40f, 20f), characterBoxes = null))

        val word = readingOrder(read).words().single()

        assertThat(word.text).isEqualTo("\u0633\u0644\u0627\u0645")
        assertThat(word.characterBoxes).isNull()
        assertThat(word.box).isEqualTo(Box(0f, 0f, 40f, 20f))
    }

    @Test
    fun `right-to-left letters`() {
        assertThat(hasRightToLeftLetter("\u0628")).isTrue()
        assertThat(hasRightToLeftLetter("\u05e9")).isTrue()
        assertThat(hasRightToLeftLetter("a1.")).isFalse()
        assertThat(hasRightToLeftLetter("")).isFalse()
    }
}
