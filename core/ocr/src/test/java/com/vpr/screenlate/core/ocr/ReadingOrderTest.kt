package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

/** Boxes are the ones Lens returned for a 1280 x 2856 test image with 64 px text. */
class ReadingOrderTest {

    private fun line(text: String, box: Box, vertical: Boolean = box.height > box.width) =
        OcrLine(listOf(OcrWord(text, "", box)), box, vertical)

    private fun layout(vararg paragraphs: OcrParagraph, engine: OcrEngineType = OcrEngineType.LENS) =
        TextLayout(OcrPage(1280, 2856, paragraphs.toList(), engine))

    private fun TextLayout.textAt(x: Float, y: Float, length: Int = 16) =
        textFrom(hitTest(x, y, tolerance = 4f)!!, length)

    @Test
    fun `columns of a vertical paragraph are read right to left`() {
        // Lens lists the left column first.
        val layout = layout(
            OcrParagraph(
                listOf(
                    line("強を続けています。", Box(912f, 1003f, 971f, 1576f)),
                    line("私は毎日、日本語の勉", Box(998f, 1003f, 1057f, 1635f)),
                ),
            ),
        )

        assertThat(layout.textAt(1027f, 1605f)).isEqualTo("勉強を続けています。")
    }

    @Test
    fun `a column broken inside a word goes on in the next paragraph`() {
        val layout = layout(
            OcrParagraph(listOf(line("駅の近くに新しい自転", Box(558f, 1007f, 619f, 1633f)))),
            OcrParagraph(listOf(line("車屋ができました。", Box(416f, 1003f, 476f, 1572f)))),
        )

        assertThat(layout.textAt(588f, 1540f, length = 4)).isEqualTo("自転車屋")
        assertThat(layout.readingParagraphs).hasSize(1)
        // The highlight covers both columns.
        assertThat(layout.boxesFor(layout.hitTest(588f, 1540f, 4f)!!, 3)).hasSize(2)
    }

    @Test
    fun `horizontal paragraphs stay apart`() {
        // A post's header and its text: Lens groups wrapped horizontal lines itself, its paragraphs are separate things.
        val layout = layout(
            OcrParagraph(listOf(line("名前 @handle 13h", Box(190f, 930f, 914f, 981f)))),
            OcrParagraph(listOf(line("ついに", Box(186f, 1002f, 396f, 1053f)))),
        )

        assertThat(layout.readingParagraphs).hasSize(2)
        assertThat(layout.textAt(880f, 955f)).isEqualTo("h")
    }

    @Test
    fun `a column that ends above the bottom is not continued`() {
        val layout = layout(
            OcrParagraph(listOf(line("見出し", Box(558f, 1007f, 619f, 1199f)))),
            OcrParagraph(listOf(line("本文は次の列から始まる。", Box(470f, 1007f, 531f, 1775f)))),
        )

        assertThat(layout.textAt(588f, 1170f)).isEqualTo("し")
    }

    @Test
    fun `text far away, off the top margin or in another size is not continued`() {
        val first = OcrParagraph(listOf(line("駅の近くに新しい自転", Box(558f, 1007f, 619f, 1633f))))
        val far = OcrParagraph(listOf(line("車屋ができました。", Box(380f, 1003f, 440f, 1572f))))
        val lower = OcrParagraph(listOf(line("車屋ができました。", Box(476f, 1100f, 536f, 1669f))))
        val small = OcrParagraph(listOf(line("車屋ができました。", Box(476f, 1003f, 506f, 1290f))))

        for (next in listOf(far, lower, small)) {
            assertThat(layout(first, next).textAt(588f, 1540f)).isEqualTo("自転")
        }
    }

    @Test
    fun `app text is not joined across nodes`() {
        val layout = layout(
            OcrParagraph(listOf(line("駅の近くに新しい自転", Box(558f, 1007f, 619f, 1633f)))),
            OcrParagraph(listOf(line("車屋ができました。", Box(476f, 1003f, 536f, 1572f)))),
            engine = OcrEngineType.ACCESSIBILITY,
        )

        assertThat(layout.textAt(588f, 1540f)).isEqualTo("自転")
    }

    @Test
    fun `joined paragraphs keep the engine they came from`() {
        val layout = layout(
            OcrParagraph(listOf(line("駅の近くに新しい自転", Box(558f, 1007f, 619f, 1633f))), OcrEngineType.LENS),
            OcrParagraph(listOf(line("車屋ができました。", Box(476f, 1003f, 536f, 1572f))), OcrEngineType.LENS),
            engine = OcrEngineType.ACCESSIBILITY,
        )

        assertThat(layout.readingParagraphs.single().engine).isEqualTo(OcrEngineType.LENS)
    }

    @Test
    fun `a paragraph without an engine has the page's`() {
        val first = OcrParagraph(listOf(line("駅の近くに新しい自転", Box(558f, 1007f, 619f, 1633f))))
        // Added later from the same engine, e.g. a band recognized again for small text.
        val added = OcrParagraph(listOf(line("車屋ができました。", Box(476f, 1003f, 536f, 1572f))), OcrEngineType.LENS)
        val device = added.copy(engine = OcrEngineType.ML_KIT)

        assertThat(layout(first, added).textAt(588f, 1540f, length = 4)).isEqualTo("自転車屋")
        assertThat(layout(first, device).textAt(588f, 1540f, length = 4)).isEqualTo("自転")
    }
}
