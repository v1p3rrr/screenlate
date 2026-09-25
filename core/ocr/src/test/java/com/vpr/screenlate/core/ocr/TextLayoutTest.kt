package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class TextLayoutTest {

    private fun page(vararg paragraphs: OcrParagraph) = OcrPage(1000, 2000, paragraphs.toList(), OcrEngineType.LENS)

    private fun word(text: String, box: Box) = OcrWord(text, "", box)

    // "食べる" as one word 0..300 x, "ことが" as another 300..600, one horizontal line.
    private val horizontal = OcrParagraph(
        listOf(
            OcrLine(
                words = listOf(word("食べる", Box(0f, 0f, 300f, 100f)), word("ことが", Box(300f, 0f, 600f, 100f))),
                box = Box(0f, 0f, 600f, 100f),
                vertical = false,
            ),
            OcrLine(
                words = listOf(word("できる", Box(0f, 120f, 300f, 220f))),
                box = Box(0f, 120f, 300f, 220f),
                vertical = false,
            ),
        ),
    )

    // One vertical column 800..850 x, 0..400 y.
    private val vertical = OcrParagraph(
        listOf(
            OcrLine(
                words = listOf(word("縦書き", Box(800f, 0f, 850f, 300f)), word("だ", Box(800f, 300f, 850f, 400f))),
                box = Box(800f, 0f, 850f, 400f),
                vertical = true,
            ),
        ),
    )

    @Test
    fun `splits words evenly along the reading direction`() {
        val layout = TextLayout(page(horizontal, vertical))

        val first = layout.paragraphs[0]
        assertThat(first.map { it.text }).containsExactly("食", "べ", "る", "こ", "と", "が", "で", "き", "る").inOrder()
        assertThat(first[1].box).isEqualTo(Box(100f, 0f, 200f, 100f))

        val column = layout.paragraphs[1]
        assertThat(column[1].box).isEqualTo(Box(800f, 100f, 850f, 200f))
    }

    @Test
    fun `hit test finds the character under the point`() {
        val layout = TextLayout(page(horizontal, vertical))

        val hit = layout.hitTest(x = 350f, y = 50f, tolerance = 10f)

        assertThat(hit).isEqualTo(TextPosition(paragraphIndex = 0, offset = 3))
        assertThat(layout.textFrom(hit!!, maxLength = 16)).isEqualTo("ことができる")
    }

    @Test
    fun `hit test in vertical text reads downwards`() {
        val layout = TextLayout(page(horizontal, vertical))

        val hit = layout.hitTest(x = 825f, y = 150f, tolerance = 10f)!!

        assertThat(layout.textFrom(hit, maxLength = 16)).isEqualTo("書きだ")
    }

    @Test
    fun `hit test uses tolerance for points between characters`() {
        val layout = TextLayout(page(horizontal))

        assertThat(layout.hitTest(x = 50f, y = 110f, tolerance = 20f)).isNotNull()
        assertThat(layout.hitTest(x = 900f, y = 900f, tolerance = 20f)).isNull()
    }

    @Test
    fun `boxes are merged per line`() {
        val layout = TextLayout(page(horizontal))

        val boxes = layout.boxesFor(TextPosition(0, 4), length = 4)

        assertThat(boxes).containsExactly(Box(400f, 0f, 600f, 100f), Box(0f, 120f, 200f, 220f)).inOrder()
    }

    @Test
    fun `uses engine character boxes when their count matches`() {
        val charBoxes = listOf(Box(0f, 0f, 10f, 10f), Box(50f, 0f, 60f, 10f))
        val line = OcrLine(listOf(OcrWord("猫だ", "", Box(0f, 0f, 60f, 10f), charBoxes)), Box(0f, 0f, 60f, 10f), false)
        val layout = TextLayout(page(OcrParagraph(listOf(line))))

        assertThat(layout.paragraphs[0].map { it.box }).isEqualTo(charBoxes)
    }
}
