package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PageMergeTest {

    private fun line(text: String, box: Box) = OcrLine(listOf(OcrWord(text, "", box)), box, vertical = false)

    private fun page(engine: OcrEngineType, vararg lines: OcrLine) =
        OcrPage(1000, 2000, lines.map { OcrParagraph(listOf(it)) }, engine)

    @Test
    fun `adds only lines where the page has no text`() {
        val app = page(OcrEngineType.ACCESSIBILITY, line("本文", Box(0f, 0f, 600f, 100f)))
        val ocr = page(
            OcrEngineType.LENS,
            line("本文", Box(5f, 2f, 590f, 98f)),
            line("総合力", Box(700f, 1300f, 780f, 1330f)),
        )

        val merged = app.withMissingFrom(ocr)

        assertThat(merged.paragraphs.map { it.text }).containsExactly("本文", "総合力").inOrder()
        assertThat(merged.engine).isEqualTo(OcrEngineType.ACCESSIBILITY)
        assertThat(merged.paragraphs.map { it.engine }).containsExactly(null, OcrEngineType.LENS).inOrder()
    }

    @Test
    fun `a slight touch between neighbouring lines is not an overlap`() {
        val page = page(OcrEngineType.LENS, line("上", Box(0f, 0f, 500f, 100f)))
        val band = page(OcrEngineType.LENS, line("下", Box(0f, 95f, 500f, 195f)))

        assertThat(page.withMissingFrom(band).paragraphs).hasSize(2)
    }

    @Test
    fun `nothing to add returns the same page`() {
        val page = page(OcrEngineType.LENS, line("同じ", Box(0f, 0f, 500f, 100f)))

        assertThat(page.withMissingFrom(page)).isSameInstanceAs(page)
    }

    @Test
    fun `bands cover the screen with half overlaps`() {
        val bands = ScreenBands.of(1153, 2560)

        assertThat(bands.first().top).isEqualTo(0f)
        assertThat(bands.last().bottom).isEqualTo(2560f)
        bands.zipWithNext().forEach { (a, b) -> assertThat(b.top).isLessThan(a.bottom) }
        assertThat(bands.size).isIn(4..6)
        assertThat(ScreenBands.nearest(bands, 1280f)).isNotEqualTo(0)
    }
}
