package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Test

class PaddleParagraphsTest {
    @Test
    fun `a tilted long row uses character height instead of the inflated whole row box`() {
        val first = row(42f, 202f, 1116f, 293f).copy(words = listOf(
            OcrWord("word", "", Box(42f, 202f, 1116f, 293f),
                characterBoxes = listOf(Box(42f, 202f, 92f, 276f))),
        ))
        val second = row(45f, 295f, 835f, 361f)
        assertThat(PaddleParagraphs.from(listOf(first, second)).single().lines).containsExactly(first, second).inOrder()
        val overlapping = row(43f, 1083f, 1088f, 1155f).copy(words = listOf(
            OcrWord("word", "", Box(43f, 1083f, 1088f, 1155f), listOf(Box(43f, 1083f, 63f, 1131f))),
        ))
        val next = row(48f, 1140f, 1037f, 1219f).copy(words = listOf(
            OcrWord("word", "", Box(48f, 1140f, 1037f, 1219f), listOf(Box(48f, 1140f, 68f, 1188f))),
        ))
        assertThat(PaddleParagraphs.from(listOf(overlapping, next)).single().lines).containsExactly(overlapping, next).inOrder()
        val padded = row(44f, 1139f, 1039f, 1221f).copy(words = listOf(
            OcrWord("word", "", Box(44f, 1139f, 1039f, 1221f), listOf(Box(44f, 1139f, 64f, 1205.5f))),
        ))
        val shorter = row(44f, 1214f, 751f, 1271f).copy(words = listOf(
            OcrWord("word", "", Box(44f, 1214f, 751f, 1271f), listOf(Box(44f, 1214f, 64f, 1263.1f))),
        ))
        assertThat(PaddleParagraphs.from(listOf(padded, shorter)).single().lines).containsExactly(padded, shorter).inOrder()
    }

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
