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
        assertThat(TextRecognition.widths(100, 50)).isEqualTo(96 to 320)
        assertThat(TextRecognition.widths(1000, 40)).isEqualTo(1200 to 1216)
        assertThat(TextRecognition.widths(1, 400)).isEqualTo(1 to 320)
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

    @Test
    fun `words split at spaces and reach halfway to their neighbours`() {
        val line = DecodedLine(listOf("а", "б", " ", "в"), listOf(0.1f, 0.2f, 0.3f, 0.4f), 1f)
        // The crop took half the input: positions double across the 100-pixel box.
        val words = TextRecognition.words(line, Box(0f, 10f, 100f, 30f), contentShare = 0.5f)
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
        val words = TextRecognition.words(line, Box(0f, 10f, 100f, 30f), contentShare = 0.5f)
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
        val words = TextRecognition.words(line, Box(10f, 0f, 30f, 100f), contentShare = 0.5f, vertical = true)
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
    fun `a line of spaces has no words`() {
        val line = DecodedLine(listOf(" ", " "), listOf(0.1f, 0.2f), 1f)
        assertThat(TextRecognition.words(line, Box(0f, 0f, 10f, 10f), 1f)).isEmpty()
    }
}
