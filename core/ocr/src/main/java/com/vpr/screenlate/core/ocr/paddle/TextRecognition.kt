package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrWord
import kotlin.math.ceil

/** A line as the recognizer read it: its characters, each at a position across the input (0 to 1), and its score. */
internal data class DecodedLine(val characters: List<String>, val positions: List<Float>, val score: Float)

/**
 * Input and output of PP-OCR's text recognizer (CTC head). A line's crop is scaled to [HEIGHT] pixels keeping its
 * proportions, as BGR planes scaled to -1..1, and padded on the right to at least [MIN_WIDTH] and to a multiple of
 * [WIDTH_STEP]. The output gives each step across the input a probability per class: class 0 is CTC's blank, the
 * others the model's dictionary with a space last.
 */
internal object TextRecognition {
    const val HEIGHT = 48
    const val MIN_WIDTH = 320
    const val WIDTH_STEP = 32

    /** The width a crop of [width] × [height] is scaled to, and the input width with the padding. */
    fun widths(width: Float, height: Float): Pair<Int, Int> {
        val content = maxOf(1, ceil(HEIGHT * width / height).toInt())
        val padded = maxOf(MIN_WIDTH, (content + WIDTH_STEP - 1) / WIDTH_STEP * WIDTH_STEP)
        return content to padded
    }

    /** ARGB [pixels] of the scaled crop, [content] pixels wide, as an input [width] wide; the padding is zero. */
    fun input(pixels: IntArray, content: Int, width: Int): Tensor {
        val plane = HEIGHT * width
        val data = FloatArray(3 * plane)
        for (y in 0 until HEIGHT) {
            for (x in 0 until content) {
                val pixel = pixels[y * content + x]
                val i = y * width + x
                data[i] = (pixel and 0xFF) / 127.5f - 1f
                data[plane + i] = (pixel shr 8 and 0xFF) / 127.5f - 1f
                data[2 * plane + i] = (pixel shr 16 and 0xFF) / 127.5f - 1f
            }
        }
        return Tensor(data, intArrayOf(1, 3, HEIGHT, width))
    }

    /** Greedy CTC decoding of an output shaped `[1, steps, classes]`; [dictionary] has the blank first. */
    fun decode(output: Tensor, dictionary: List<String>): DecodedLine {
        val steps = output.shape[1]
        val classes = output.shape[2]
        require(classes == dictionary.size) { "The output has $classes classes, the dictionary ${dictionary.size}" }
        val characters = ArrayList<String>()
        val positions = ArrayList<Float>()
        var scores = 0f
        var previous = 0
        for (step in 0 until steps) {
            val offset = step * classes
            var best = 0
            for (c in 1 until classes) {
                if (output.data[offset + c] > output.data[offset + best]) best = c
            }
            if (best != 0 && best != previous) {
                characters += dictionary[best]
                positions += (step + 0.5f) / steps
                scores += output.data[offset + best]
            }
            previous = best
        }
        return DecodedLine(characters, positions, if (characters.isEmpty()) 0f else scores / characters.size)
    }

    /**
     * The words of [line] read in [area]: the characters between spaces, each word and each character reaching
     * halfway to the character before it and after it, as bounding boxes cut to [limit]. [contentShare] is the part
     * of the input width the crop took; the input's width runs along the area.
     */
    fun words(line: DecodedLine, area: OrientedBox, contentShare: Float, limit: Box): List<OcrWord> {
        val characters = line.characters
        val end = area.length
        val at = line.positions.map { (it / contentShare * end).coerceAtMost(end) }
        fun before(i: Int) = if (i == 0) 0f else (at[i - 1] + at[i]) / 2
        fun after(i: Int) = if (i == characters.lastIndex) end else (at[i] + at[i + 1]) / 2
        fun span(from: Float, to: Float) = area.part(from, to).within(limit)
        val words = ArrayList<OcrWord>()
        var first = -1
        for (i in 0..characters.size) {
            val space = i == characters.size || characters[i].isBlank()
            if (!space && first < 0) first = i
            if (!space || first < 0) continue
            words += OcrWord(
                text = characters.subList(first, i).joinToString(""),
                separator = if (i < characters.size) " " else "",
                box = span(before(first), after(i - 1)),
                characterBoxes = (first until i).map { span(before(it), after(it)) },
            )
            first = -1
        }
        return words
    }
}
