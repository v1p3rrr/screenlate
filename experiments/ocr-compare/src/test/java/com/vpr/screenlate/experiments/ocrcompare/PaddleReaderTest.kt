package com.vpr.screenlate.experiments.ocrcompare

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Test

class PaddleReaderTest {
    private fun word(text: String, separator: String, left: Float) = OcrWord(
        text,
        separator,
        Box(left, 0f, left + 10f, 10f),
        listOf(Box(left, 0f, left + 5f, 10f), Box(left + 5f, 0f, left + 10f, 10f)),
    )

    @Test
    fun `a right-to-left line is reversed with each separator after its word in reading order`() {
        // On the screen from the left: "ab", "cd" glued to "ef", then the end of the line.
        val words = listOf(word("ab", " ", 0f), word("cd", "", 20f), word("ef", "", 30f))
        val read = inReadingOrder(OcrLine(words, Box(0f, 0f, 40f, 10f), vertical = false))
        assertThat(read.words.map { it.text }).containsExactly("fe", "dc", "ba").inOrder()
        assertThat(read.words.map { it.separator }).containsExactly("", " ", "").inOrder()
        assertThat(read.text).isEqualTo("fedc ba")
        assertThat(read.rightToLeft).isTrue()
        assertThat(read.words[0].characterBoxes!!.first().left).isEqualTo(35f)
    }
}
