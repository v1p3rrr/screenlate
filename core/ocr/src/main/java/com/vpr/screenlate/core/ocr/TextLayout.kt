package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/**
 * One character of recognized text.
 *
 * @property offset index of the character inside its paragraph.
 */
data class OcrCharacter(
    val text: String,
    val box: Box,
    val paragraphIndex: Int,
    val lineIndex: Int,
    val offset: Int,
    val vertical: Boolean,
    val rightToLeft: Boolean = false,
    val engine: OcrEngineType? = null,
)

/** Position of a character inside a [TextLayout]. */
data class TextPosition(val paragraphIndex: Int, val offset: Int)

/**
 * Character-level index over an [OcrPage] used for hit testing and highlighting.
 *
 * Engines report boxes per word; when per-character boxes are missing the word box is split evenly along the
 * reading direction: down a vertical line, leftwards along a right-to-left one. A combining mark written above or
 * below its letter (Thai ้, ุ) takes no share and gets its letter's box, and so does a Khmer consonant written below
 * the one before it (after the coeng ្). Words and lines inside a paragraph are
 * joined by each word's separator, which is empty for Japanese, so a phrase broken across lines is still found.
 * Paragraphs are taken in reading order ([ReadingOrder]), so text runs on across a broken line.
 */
class TextLayout(val page: OcrPage) {

    /** The page's paragraphs in reading order; [TextPosition.paragraphIndex] and [paragraphs] follow this list. */
    val readingParagraphs: List<OcrParagraph> = ReadingOrder.paragraphs(page)

    val paragraphs: List<List<OcrCharacter>> = readingParagraphs.mapIndexed { paragraphIndex, paragraph ->
        buildList {
            paragraph.lines.forEachIndexed { lineIndex, line ->
                line.words.forEach { word ->
                    splitWord(word, line).forEach { (text, box) ->
                        add(OcrCharacter(text, box, paragraphIndex, lineIndex, size, line.vertical, line.rightToLeft, word.engine))
                    }
                }
            }
        }
    }

    private val characters: List<OcrCharacter> = paragraphs.flatten()
    private val spatialIndex by lazy { BoxIndex(characters.map { it.box }) }

    /** Builds the hit-test index now, on the calling thread, instead of at the first [hitTest]. */
    fun prepare() {
        spatialIndex
    }

    /**
     * Finds the character at ([x], [y]). If no character contains the point, returns the nearest one within
     * [tolerance] pixels.
     */
    fun hitTest(x: Float, y: Float, tolerance: Float): TextPosition? {
        var best: OcrCharacter? = null
        var bestDistance = Float.MAX_VALUE
        val reach = maxOf(0f, tolerance)
        for (index in spatialIndex.query(Box(x - reach, y - reach, x + reach, y + reach))) {
            val character = characters[index]
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

    /**
     * Text of the paragraph containing [position], and the UTF-16 index of that character in it
     * (characters are code points, so the two can differ).
     */
    fun paragraphText(position: TextPosition): Pair<String, Int> {
        val characters = paragraphs[position.paragraphIndex]
        val index = characters.take(position.offset).sumOf { it.text.length }
        return characters.joinToString("") { it.text } to index
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

    private fun splitWord(word: OcrWord, line: OcrLine): List<Pair<String, Box>> {
        val glyphs = codePoints(word.text)
        // A Latin word or a number inside a right-to-left line runs left to right.
        val rightToLeft = !line.vertical && isRightToLeftWord(word.text, line.rightToLeft)
        val boxes = word.characterBoxes?.takeIf { it.size == glyphs.size } ?: splitBox(word.box, glyphs, line.vertical, rightToLeft)
        val result = glyphs.zip(boxes).toMutableList()
        codePoints(word.separator).forEach { separator ->
            val edge = when {
                line.vertical -> Box(word.box.left, word.box.bottom, word.box.right, word.box.bottom)
                line.rightToLeft -> Box(word.box.left, word.box.top, word.box.left, word.box.bottom)
                else -> Box(word.box.right, word.box.top, word.box.right, word.box.bottom)
            }
            result += separator to edge
        }
        return result
    }

    private fun splitBox(box: Box, glyphs: List<String>, vertical: Boolean, rightToLeft: Boolean): List<Box> {
        if (glyphs.size <= 1) return List(glyphs.size) { box }
        // Marks after the word's first glyph share the slot of the glyph before them, and so do subscript consonants.
        val slots = IntArray(glyphs.size)
        var count = 0
        glyphs.forEachIndexed { index, glyph ->
            if (takesPlace(glyph.codePointAt(0), glyphs.getOrNull(index - 1)?.codePointAt(0))) count++
            slots[index] = count - 1
        }
        return List(glyphs.size) { index ->
            val slot = slots[index]
            when {
                vertical -> {
                    val step = box.height / count
                    Box(box.left, box.top + step * slot, box.right, box.top + step * (slot + 1))
                }
                rightToLeft -> {
                    val step = box.width / count
                    Box(box.right - step * (slot + 1), box.top, box.right - step * slot, box.bottom)
                }
                else -> {
                    val step = box.width / count
                    Box(box.left + step * slot, box.top, box.left + step * (slot + 1), box.bottom)
                }
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
