package com.vpr.screenlate.core.ocr.tesseract

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import org.junit.Test

class TesseractPageTest {
    private fun box(left: Int, right: Int, top: Int = 0, bottom: Int = 10) =
        Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    @Test
    fun `paragraphs, lines and words keep their boxes and the language's separator`() {
        val page = tesseractPage(
            listOf(
                listOf(
                    TesseractLine(box(0, 60), listOf(TesseractWord("Hello", box(0, 25), emptyList()), TesseractWord("world", box(30, 60), emptyList()))),
                    TesseractLine(box(0, 20, 12, 22), listOf(TesseractWord("Bye", box(0, 20, 12, 22), emptyList()))),
                ),
                listOf(TesseractLine(box(0, 30, 40, 50), listOf(TesseractWord("Next", box(0, 30, 40, 50), emptyList())))),
            ),
            width = 100,
            height = 60,
            separator = " ",
        )
        assertThat(page.engine).isEqualTo(OcrEngineType.DEVICE_MODEL)
        assertThat(page.paragraphs.map { it.text }).containsExactly("Hello world\nBye", "Next").inOrder()
        assertThat(page.paragraphs[0].lines[0].box).isEqualTo(box(0, 60))
        assertThat(page.paragraphs[0].lines[0].vertical).isFalse()
    }

    @Test
    fun `clusters of a language without spaces join, and a wide gap is a space`() {
        // ห้อง สมุด read as clusters, a line 100 high: gaps of 20 join, a gap of 50 is a space.
        val words = listOf(
            TesseractWord("ห้", box(0, 40, 0, 100), emptyList()),
            TesseractWord("อง", box(60, 120, 0, 100), emptyList()),
            TesseractWord("สมุด", box(170, 260, 0, 100), emptyList()),
        )
        val line = TesseractLine(box(0, 260, 0, 100), words)
        val page = tesseractPage(listOf(listOf(line, line)), 300, 200, separator = "", spaceGap = 0.4f)
        assertThat(page.paragraphs.single().text).isEqualTo("ห้อง สมุด\nห้อง สมุด")
        assertThat(page.paragraphs.single().lines[0].words.last().separator).isEmpty()
        // Without a gap rule every word takes the language's separator.
        assertThat(tesseractPage(listOf(listOf(line)), 300, 200, separator = "").paragraphs.single().text).isEqualTo("ห้องสมุด")
    }

    @Test
    fun `symbol boxes stay only with one per character inside the word`() {
        val kept = TesseractWord("ab", box(0, 20), listOf(box(0, 10), box(10, 20)))
        val counted = TesseractWord("été", box(30, 60), listOf(box(30, 40), box(40, 50)))
        val outside = TesseractWord("cd", box(70, 90), listOf(box(70, 80), box(80, 95)))
        val line = tesseractPage(listOf(listOf(TesseractLine(box(0, 90), listOf(kept, counted, outside)))), 100, 10, " ")
            .paragraphs.single().lines.single()
        assertThat(line.words.map { it.characterBoxes }).containsExactly(listOf(box(0, 10), box(10, 20)), null, null).inOrder()
    }

    @Test
    fun `blank words and the lines and paragraphs they leave empty are dropped`() {
        val page = tesseractPage(
            listOf(
                listOf(TesseractLine(box(0, 10), listOf(TesseractWord(" ", box(0, 10), emptyList())))),
                listOf(TesseractLine(box(0, 10), listOf(TesseractWord("שלום", box(0, 10), emptyList())))),
            ),
            100,
            10,
            " ",
        )
        assertThat(page.paragraphs).hasSize(1)
        assertThat(page.paragraphs.single().lines.single().rightToLeft).isTrue()
    }
}
