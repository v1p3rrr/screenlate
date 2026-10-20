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
    fun `digits of every script keep their order and signs at a number's edge stand apart`() {
        // "الرصيف 5." is drawn with its stop at the left, "عام ٢٠٢٤" with the year left to right.
        val stop = page(word(".5", 0f), word("فيصرلا", 30f, separator = ""))
        assertThat(readingOrder(stop).words().map { it.text }).containsExactly("الرصيف", "5.").inOrder()
        val year = page(word("٢٠٢٤", 0f), word("ماع", 50f, separator = ""))
        assertThat(readingOrder(year).words().map { it.text }).containsExactly("عام", "٢٠٢٤").inOrder()
        val persian = page(word("۱۴۰۳", 0f), word("لاس", 50f, separator = ""))
        assertThat(readingOrder(persian).words().map { it.text }).containsExactly("سال", "۱۴۰۳").inOrder()
        // "الوقت: 10:30": the colon after the word stands apart from the time; a decimal one stays inside.
        val time = page(word("10:30", 0f), word(":تقولا", 60f, separator = ""))
        assertThat(readingOrder(time).words().map { it.text }).containsExactly("الوقت:", "10:30").inOrder()
        // "بنسبة -12.5 50%" among Arabic letters: each number left to right, the signs on its other side.
        val percent = page(word("%50", 0f), word("12.5-", 40f), word("ةبسنب", 100f, separator = ""))
        assertThat(readingOrder(percent).words().map { it.text }).containsExactly("بنسبة", "-12.5", "50%").inOrder()
        val thousands = page(word("1,000", 0f), word("ىلع", 60f, separator = ""))
        assertThat(readingOrder(thousands).words().map { it.text }).containsExactly("على", "1,000").inOrder()
    }

    @Test
    fun `brackets and guillemets come back the way they face in the text`() {
        // "«اقرأ» (ص 12)" drawn right to left: read from the left as "(12 ص) «أرقا»".
        val read = page(word("(12", 0f), word("ص)", 40f), word("«أرقا»", 70f, separator = ""))

        val words = readingOrder(read).words()

        assertThat(words.map { it.text }).containsExactly("«اقرأ»", "(ص", "12)").inOrder()
    }

    @Test
    fun `a Latin run keeps its numbers and inner signs, its edge signs stand apart`() {
        // "اشتريت iPhone 15, ثم iOS." drawn right to left, the comma and the stop left of their runs.
        val read = page(word(".iOS", 0f), word("مث", 50f), word(",iPhone", 80f), word("15", 160f), word("تيرتشا", 190f, separator = ""))

        assertThat(readingOrder(read).words().map { it.text })
            .containsExactly("اشتريت", "iPhone", "15,", "ثم", "iOS.").inOrder()
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
