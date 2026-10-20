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
 * turns it: a line's characters are reversed, except runs of Latin letters with the digits, spaces and signs
 * `: * . , / % + -` among them, and numbers in digits of any script, which keep their order. Among Arabic letters the
 * line was drawn by the bidirectional algorithm, where a number holds together only through `. , : / ٫ ٬` between two
 * digits (10:30, 12.5): signs at a run's edge and a number's `% + - *` stand apart and are drawn on its other side
 * ("الرصيف 5." is read ".5 فيصرلا", "50%" "%50"), and numbers apart by a space are drawn in turn. Brackets and quotes
 * outside Latin runs are mirrored, as they were drawn. Words are split again at the spaces; their boxes join their
 * characters'. Lines become [OcrLine.rightToLeft].
 */
internal fun readingOrder(page: OcrPage): OcrPage = page.copy(
    paragraphs = page.paragraphs.map { paragraph -> paragraph.copy(lines = paragraph.lines.map(::readingOrder)) },
)

/** A character with its box; [exact] when the box is the character's own, not its word's. */
private class Glyph(val text: String, val box: Box?, val exact: Boolean) {
    val space: Boolean get() = text == " "
    val latin: Boolean get() = text.all { it in 'a'..'z' || it in 'A'..'Z' }
    val digit: Boolean get() = text.all(Char::isDigit)
    val sign: Boolean get() = text.length == 1 && text[0] in KEPT_SIGNS
    val kept: Boolean get() = space || latin || digit || sign

    fun mirrored(): Glyph = MIRRORED[text]?.let { Glyph(it, box, exact) } ?: this
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
        if (run.any(Glyph::latin)) {
            val first = run.indexOfFirst { !it.space && !it.sign }
            val last = run.indexOfLast { !it.space && !it.sign }
            run.subList(0, first).forEach { segments += listOf(it) }
            segments += run.subList(first, last + 1).toList()
            run.subList(last + 1, run.size).forEach { segments += listOf(it) }
        } else {
            var number = ArrayList<Glyph>()
            run.forEachIndexed { i, glyph ->
                val joins = glyph.digit ||
                    glyph.text in NUMBER_SEPARATORS && run.getOrNull(i - 1)?.digit == true && run.getOrNull(i + 1)?.digit == true
                if (joins) {
                    number += glyph
                } else {
                    if (number.isNotEmpty()) segments += number.also { number = ArrayList() }
                    segments += listOf(glyph.mirrored())
                }
            }
            if (number.isNotEmpty()) segments += number
        }
        run.clear()
    }
    for (glyph in glyphs) {
        if (glyph.kept) {
            run += glyph
        } else {
            endRun()
            segments += listOf(glyph.mirrored())
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

private const val KEPT_SIGNS = ":*.,/%+-٫٬"

/** Signs that join the digits on either side of them into one number. */
private val NUMBER_SEPARATORS = setOf(".", ",", ":", "/", "٫", "٬")

/** Characters a right-to-left line draws as their mirror image (Unicode's Bidi_Mirrored ones in common use). */
private val MIRRORED: Map<String, String> = listOf("()", "[]", "{}", "<>", "«»", "‹›")
    .flatMap { pair -> listOf(pair[0].toString() to pair[1].toString(), pair[1].toString() to pair[0].toString()) }
    .toMap()
