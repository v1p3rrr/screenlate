package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrWord

/** Whether [text] has a letter written from right to left (Arabic, Hebrew). */
internal fun hasRightToLeftLetter(text: String): Boolean = text.codePoints().anyMatch { codePoint ->
    when (Character.getDirectionality(codePoint)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        else -> false
    }
}

/**
 * The page of a right-to-left recognizer, which reads each crop from left to right, in reading order, as PaddleOCR
 * turns it: a line's characters are reversed, except runs of Latin letters, digits and `: * . / % + -` with the spaces
 * between them, which keep their order. Words are split again at the spaces; their boxes join their characters'. Lines
 * become [OcrLine.rightToLeft].
 */
internal fun readingOrder(page: OcrPage): OcrPage = page.copy(
    paragraphs = page.paragraphs.map { paragraph -> paragraph.copy(lines = paragraph.lines.map(::readingOrder)) },
)

/** A character with its box; [exact] when the box is the character's own, not its word's. */
private class Glyph(val text: String, val box: Box?, val exact: Boolean) {
    val space: Boolean get() = text == " "
    val kept: Boolean get() = space || text.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in KEPT_SIGNS }
}

private fun readingOrder(line: OcrLine): OcrLine {
    val glyphs = ArrayList<Glyph>()
    line.words.forEachIndexed { index, word ->
        val characters = word.text.codePoints().toArray().map { String(Character.toChars(it)) }
        val boxes = word.characterBoxes?.takeIf { it.size == characters.size }
        characters.forEachIndexed { i, character -> glyphs += Glyph(character, boxes?.get(i) ?: word.box, boxes != null) }
        if (index < line.words.lastIndex) glyphs += Glyph(" ", null, exact = false)
    }
    // Segments in the recognizer's order; a kept run is one segment, its edge spaces are not part of it.
    val segments = ArrayList<List<Glyph>>()
    val run = ArrayList<Glyph>()
    fun endRun() {
        val first = run.indexOfFirst { !it.space }
        if (first < 0) {
            run.forEach { segments += listOf(it) }
        } else {
            val last = run.indexOfLast { !it.space }
            run.subList(0, first).forEach { segments += listOf(it) }
            segments += run.subList(first, last + 1).toList()
            run.subList(last + 1, run.size).forEach { segments += listOf(it) }
        }
        run.clear()
    }
    for (glyph in glyphs) {
        if (glyph.kept) {
            run += glyph
        } else {
            endRun()
            segments += listOf(glyph)
        }
    }
    endRun()
    val words = ArrayList<OcrWord>()
    val word = ArrayList<Glyph>()
    fun endWord(separator: String) {
        if (word.isEmpty()) return
        val boxes = word.mapNotNull { it.box }
        words += OcrWord(
            text = word.joinToString("") { it.text },
            separator = separator,
            box = Box.unionOf(boxes) ?: line.box,
            characterBoxes = boxes.takeIf { word.all(Glyph::exact) },
        )
        word.clear()
    }
    for (glyph in segments.asReversed().flatten()) {
        if (glyph.space) endWord(" ") else word += glyph
    }
    endWord("")
    return line.copy(words = words, rightToLeft = true)
}

private const val KEPT_SIGNS = ":*./%+-"
