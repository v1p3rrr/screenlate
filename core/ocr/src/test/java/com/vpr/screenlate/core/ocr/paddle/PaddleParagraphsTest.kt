package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Test

class PaddleParagraphsTest {
    @Test
    fun `wrapped rows join but a blank gap or another text size starts a paragraph`() {
        val rows = listOf(row(10f, 10f, 300f, 30f), row(10f, 35f, 280f, 55f),
            row(10f, 80f, 300f, 100f), row(10f, 105f, 300f, 145f))
        assertThat(PaddleParagraphs.from(rows).map { it.lines.size }).containsExactly(2, 1, 1).inOrder()
    }

    @Test
    fun `columns keep their own paragraphs and no words are reordered`() {
        val left = listOf(row(10f, 10f, 110f, 30f), row(10f, 35f, 90f, 55f))
        val right = listOf(row(160f, 10f, 260f, 30f), row(160f, 35f, 240f, 55f))
        val paragraphs = PaddleParagraphs.from(listOf(left[0], right[0], left[1], right[1]))
        assertThat(paragraphs.map { it.lines }).containsExactly(left, right).inOrder()
    }

    @Test
    fun `right aligned rows can wrap while vertical columns keep the existing continuation path`() {
        val rtl = listOf(row(10f, 10f, 300f, 30f).copy(rightToLeft = true),
            row(100f, 35f, 300f, 55f).copy(rightToLeft = true))
        assertThat(PaddleParagraphs.from(rtl).single().lines).containsExactlyElementsIn(rtl).inOrder()
        val vertical = rtl.map { it.copy(vertical = true) }
        assertThat(PaddleParagraphs.from(vertical)).hasSize(2)
        assertThat(PaddleParagraphs.from(emptyList())).isEmpty()
    }

    private fun row(left: Float, top: Float, right: Float, bottom: Float): OcrLine {
        val box = Box(left, top, right, bottom)
        return OcrLine(listOf(OcrWord("word", "", box)), box, vertical = false)
    }
}
