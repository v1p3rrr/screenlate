package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class TextRecognitionTest {

    private val dictionary = PaddlePipeline.dictionary(listOf("а", "б", "в"))

    /** An output whose steps pick the given classes with probability 0.9. */
    private fun output(vararg picks: Int): Tensor {
        val classes = dictionary.size
        val data = FloatArray(picks.size * classes) { 0.02f }
        picks.forEachIndexed { step, pick -> data[step * classes + pick] = 0.9f }
        return Tensor(data, intArrayOf(1, picks.size, classes))
    }

    @Test
    fun `crops keep their proportions at 48 pixels and are padded to a multiple of 32 and at least 320`() {
        assertThat(TextRecognition.widths(100f, 50f)).isEqualTo(96 to 320)
        assertThat(TextRecognition.widths(1000f, 40f)).isEqualTo(1200 to 1216)
        assertThat(TextRecognition.widths(1f, 400f)).isEqualTo(1 to 320)
    }

    @Test
    fun `the input is scaled to minus one to one and zero in the padding`() {
        val white = 0xFFFFFFFF.toInt()
        val input = TextRecognition.input(IntArray(48) { white }, content = 1, width = 320)
        assertThat(input.shape).asList().containsExactly(1, 3, 48, 320).inOrder()
        assertThat(input.data[0]).isWithin(1e-6f).of(1f)
        assertThat(input.data[1]).isEqualTo(0f)
    }

    @Test
    fun `repeats and blanks collapse and the space is the last class`() {
        val space = dictionary.lastIndex
        val decoded = TextRecognition.decode(output(0, 1, 1, 0, 2, space, 3, 0, 3), dictionary)
        assertThat(decoded.characters.joinToString("")).isEqualTo("аб вв")
        assertThat(decoded.positions.first()).isWithin(1e-6f).of(1.5f / 9)
        assertThat(decoded.score).isWithin(1e-6f).of(0.9f)
    }

    private val image = Box(0f, 0f, 1000f, 1000f)

    /** The line from 0 to 100 across and 10 to 30 down. */
    private val upright = OrientedBox(0f, 10f, 1f, 0f, 100f, 20f)

    @Test
    fun `words split at spaces and reach halfway to their neighbours`() {
        val line = DecodedLine(listOf("а", "б", " ", "в"), listOf(0.1f, 0.2f, 0.3f, 0.4f), 1f)
        // The crop took half the input: positions double across the 100-pixel box.
        val words = TextRecognition.words(line, upright, contentShare = 0.5f, image)
        assertThat(words.map { it.text }).containsExactly("аб", "в").inOrder()
        assertThat(words.map { it.separator }).containsExactly(" ", "").inOrder()
        assertThat(words[0].box.left).isEqualTo(0f)
        assertThat(words[0].box.right).isWithin(1e-4f).of(50f)
        assertThat(words[1].box.left).isWithin(1e-4f).of(70f)
        assertThat(words[1].box.right).isEqualTo(100f)
        assertThat(words[1].box.top).isEqualTo(10f)
    }

    @Test
    fun `characters reach halfway to their neighbours inside the word`() {
        val line = DecodedLine(listOf("а", "б", " ", "в"), listOf(0.1f, 0.2f, 0.3f, 0.4f), 1f)
        val words = TextRecognition.words(line, upright, contentShare = 0.5f, image)
        val first = words[0].characterBoxes!!
        assertThat(first).hasSize(2)
        assertThat(first[0].left).isEqualTo(0f)
        assertThat(first[0].right).isWithin(1e-4f).of(30f)
        assertThat(first[1].left).isWithin(1e-4f).of(30f)
        assertThat(first[1].right).isWithin(1e-4f).of(50f)
        assertThat(words[1].characterBoxes!!.single()).isEqualTo(words[1].box)
    }

    @Test
    fun `a vertical line spreads its characters down the box`() {
        val line = DecodedLine(listOf("а", "б", " ", "в"), listOf(0.1f, 0.2f, 0.3f, 0.4f), 1f)
        val column = OrientedBox(10f, 0f, 1f, 0f, 20f, 100f).turned()
        val words = TextRecognition.words(line, column, contentShare = 0.5f, image)
        assertThat(words.map { it.text }).containsExactly("аб", "в").inOrder()
        assertThat(words[0].box.top).isEqualTo(0f)
        assertThat(words[0].box.bottom).isWithin(1e-4f).of(50f)
        assertThat(words[0].box.left).isEqualTo(10f)
        assertThat(words[0].box.right).isEqualTo(30f)
        assertThat(words[0].characterBoxes!![1].top).isWithin(1e-4f).of(30f)
        assertThat(words[1].box.top).isWithin(1e-4f).of(70f)
        assertThat(words[1].box.bottom).isEqualTo(100f)
    }

    @Test
    fun `a turned line's words are the bounding boxes of their stretches, cut to the image`() {
        val line = DecodedLine(listOf("а", " ", "б"), listOf(0.25f, 0.5f, 0.75f), 1f)
        // Along (0.6, 0.8) from (−4, 0), 100 long and 10 thick: across is (−0.8, 0.6).
        val turned = OrientedBox(-4f, 0f, 0.6f, 0.8f, 100f, 10f)
        val words = TextRecognition.words(line, turned, contentShare = 1f, image)
        assertThat(words.map { it.text }).containsExactly("а", "б").inOrder()
        // The first word runs 0 to 37.5 along: its corners reach x = −12, cut to 0.
        assertThat(words[0].box.left).isEqualTo(0f)
        assertThat(words[0].box.top).isWithin(1e-4f).of(0f)
        assertThat(words[0].box.right).isWithin(1e-4f).of(-4f + 37.5f * 0.6f)
        assertThat(words[0].box.bottom).isWithin(1e-4f).of(37.5f * 0.8f + 6f)
        // The second runs 62.5 to 100.
        assertThat(words[1].box.left).isWithin(1e-4f).of(-4f + 62.5f * 0.6f - 8f)
        assertThat(words[1].box.bottom).isWithin(1e-4f).of(80f + 6f)
    }

    @Test
    fun `a line of spaces has no words`() {
        val line = DecodedLine(listOf(" ", " "), listOf(0.1f, 0.2f), 1f)
        assertThat(TextRecognition.words(line, OrientedBox(0f, 0f, 1f, 0f, 10f, 10f), 1f, image)).isEmpty()
    }
}
