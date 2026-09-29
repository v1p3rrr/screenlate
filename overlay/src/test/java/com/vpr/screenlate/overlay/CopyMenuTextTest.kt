package com.vpr.screenlate.overlay

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import org.junit.Test

class CopyMenuTextTest {

    private fun paragraph(text: String, top: Float): OcrParagraph {
        val box = Box(0f, top, 100f * text.length, top + 100f)
        return OcrParagraph(listOf(OcrLine(listOf(OcrWord(text, "", box)), box, vertical = false)))
    }

    private fun layout(vararg texts: String) =
        TextLayout(OcrPage(1000, 2000, texts.mapIndexed { i, text -> paragraph(text, i * 200f) }, OcrEngineType.LENS))

    private val current = layout(" 食べる ", "できる")

    @Test
    fun `copies the paragraph under the aim`() {
        val shown = layout("前の") to TextPosition(0, 0)
        assertThat(CopyMenuText.paragraph(current, TextPosition(1, 2), shown)).isEqualTo("できる")
    }

    @Test
    fun `copies the word of the popup when the aim is off text`() {
        val older = layout("前の", "結果")
        assertThat(CopyMenuText.paragraph(current, null, older to TextPosition(1, 0))).isEqualTo("結果")
    }

    @Test
    fun `offers no paragraph without the aim on text and without a popup`() {
        assertThat(CopyMenuText.paragraph(current, null, null)).isEmpty()
    }

    @Test
    fun `all text puts every paragraph on its own line`() {
        assertThat(CopyMenuText.all(current, "\n")).isEqualTo("食べる\nできる")
    }
}
