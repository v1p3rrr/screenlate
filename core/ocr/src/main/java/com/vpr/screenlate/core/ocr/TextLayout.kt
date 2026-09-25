package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/**
 * One character of recognized text.
 *
 * @property offset index of the character inside its paragraph.
 * @property wordIndex index of the engine's word inside the paragraph.
 */
data class OcrCharacter(
    val text: String,
    val box: Box,
    val paragraphIndex: Int,
    val lineIndex: Int,
    val wordIndex: Int,
    val offset: Int,
    val vertical: Boolean,
)

/** Position of a character inside a [TextLayout]. */
data class TextPosition(val paragraphIndex: Int, val offset: Int)

/**
 * Character-level index over an [OcrPage] used for hit testing and highlighting.
 *
 * Engines report boxes per word; when per-character boxes are missing the word box is split evenly along the
 * reading direction. Lines inside a paragraph are concatenated without separators, which is correct for Japanese.
 */
class TextLayout(val page: OcrPage) {

    val paragraphs: List<List<OcrCharacter>> = page.paragraphs.mapIndexed { paragraphIndex, paragraph ->
        buildList {
            var wordIndex = 0
            paragraph.lines.forEachIndexed { lineIndex, line ->
                line.words.forEach { word ->
                    splitWord(word, line.vertical).forEach { (text, box) ->
                        add(OcrCharacter(text, box, paragraphIndex, lineIndex, wordIndex, size, line.vertical))
                    }
                    wordIndex++
                }
            }
        }
    }

    private val characters: List<OcrCharacter> = paragraphs.flatten()

    /**
     * Finds the character at ([x], [y]). If no character contains the point, returns the nearest one within
     * [tolerance] pixels.
     */
    fun hitTest(x: Float, y: Float, tolerance: Float): TextPosition? {
        var best: OcrCharacter? = null
        var bestDistance = Float.MAX_VALUE
        for (character in characters) {
            val distance = if (character.box.contains(x, y)) {
                // Prefer the character whose center is closest when boxes overlap.
                -1f / (1f + distanceToCenter(character.box, x, y))
            } else {
                character.box.distanceTo(x, y)
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = character
            }
        }
        val hit = best ?: return null
        if (bestDistance > tolerance) return null
        return TextPosition(hit.paragraphIndex, hit.offset)
    }

    /** Text starting at [position], up to [maxLength] characters, not crossing the paragraph end. */
    fun textFrom(position: TextPosition, maxLength: Int): String =
        paragraphs[position.paragraphIndex]
            .drop(position.offset)
            .take(maxLength)
            .joinToString("") { it.text }

    fun characterAt(position: TextPosition): OcrCharacter = paragraphs[position.paragraphIndex][position.offset]

    /** Number of characters from [position] to the end of the engine's word that contains it. */
    fun remainingInWord(position: TextPosition): Int {
        val characters = paragraphs[position.paragraphIndex]
        val wordIndex = characters[position.offset].wordIndex
        return characters.drop(position.offset).takeWhile { it.wordIndex == wordIndex }.size
    }

    /** Text of the line containing [position]. */
    fun lineText(position: TextPosition): String {
        val characters = paragraphs[position.paragraphIndex]
        val lineIndex = characters[position.offset].lineIndex
        return characters.filter { it.lineIndex == lineIndex }.joinToString("") { it.text }
    }

    /** Boxes covering [length] characters from [position], merged into one box per line. */
    fun boxesFor(position: TextPosition, length: Int): List<Box> =
        paragraphs[position.paragraphIndex]
            .drop(position.offset)
            .take(length)
            .groupBy { it.lineIndex }
            .values
            .mapNotNull { characters -> Box.unionOf(characters.map { it.box }) }

    /** One box per recognized line, for highlighting everything that was recognized. */
    fun lineBoxes(): List<Box> = page.paragraphs.flatMap { paragraph -> paragraph.lines.map { it.box } }

    private fun distanceToCenter(box: Box, x: Float, y: Float): Float {
        val dx = box.centerX - x
        val dy = box.centerY - y
        return dx * dx + dy * dy
    }

    private fun splitWord(word: OcrWord, vertical: Boolean): List<Pair<String, Box>> {
        val glyphs = codePoints(word.text)
        val boxes = word.characterBoxes?.takeIf { it.size == glyphs.size } ?: splitBox(word.box, glyphs.size, vertical)
        val result = glyphs.zip(boxes).toMutableList()
        codePoints(word.separator).forEach { separator ->
            val edge = if (vertical) {
                Box(word.box.left, word.box.bottom, word.box.right, word.box.bottom)
            } else {
                Box(word.box.right, word.box.top, word.box.right, word.box.bottom)
            }
            result += separator to edge
        }
        return result
    }

    private fun splitBox(box: Box, count: Int, vertical: Boolean): List<Box> {
        if (count <= 1) return List(count) { box }
        return List(count) { index ->
            if (vertical) {
                val step = box.height / count
                Box(box.left, box.top + step * index, box.right, box.top + step * (index + 1))
            } else {
                val step = box.width / count
                Box(box.left + step * index, box.top, box.left + step * (index + 1), box.bottom)
            }
        }
    }

    private fun codePoints(text: String): List<String> {
        val result = ArrayList<String>(text.length)
        var index = 0
        while (index < text.length) {
            val next = text.offsetByCodePoints(index, 1)
            result += text.substring(index, next)
            index = next
        }
        return result
    }
}
