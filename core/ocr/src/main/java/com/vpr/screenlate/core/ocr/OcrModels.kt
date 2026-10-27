package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.support

enum class OcrEngineType {
    LENS,
    ML_KIT,

    /** An on-device engine with downloaded models ([com.vpr.screenlate.core.ocr.model.ModelOcrEngine]). */
    DEVICE_MODEL,

    /** Not OCR: text read from the app's accessibility node tree. */
    ACCESSIBILITY,
}

/** Text recognized on one image. All coordinates are in pixels of that image. */
data class OcrPage(
    val width: Int,
    val height: Int,
    val paragraphs: List<OcrParagraph>,
    val engine: OcrEngineType,
) {
    val text: String get() = paragraphs.joinToString("\n\n") { it.text }

    /** Returns a copy with every box shifted, e.g. from window to screen coordinates. */
    fun offset(dx: Float, dy: Float): OcrPage = mapBoxes { it.offset(dx, dy) }

    /** Returns the page of the same image scaled to [width] × [height] pixels, every box scaled with it. */
    fun scaled(width: Int, height: Int): OcrPage {
        val x = width.toFloat() / this.width
        val y = height.toFloat() / this.height
        return mapBoxes { Box(it.left * x, it.top * y, it.right * x, it.bottom * y) }.copy(width = width, height = height)
    }

    private fun mapBoxes(transform: (Box) -> Box): OcrPage = copy(
        paragraphs = paragraphs.map { paragraph ->
            paragraph.copy(
                lines = paragraph.lines.map { line ->
                    line.copy(
                        box = transform(line.box),
                        words = line.words.map { word ->
                            word.copy(box = transform(word.box), characterBoxes = word.characterBoxes?.map(transform))
                        },
                    )
                },
            )
        },
    )
}

/**
 * The page with every word as [language] writes it ([LanguageSupport.ocrLines], [LanguageSupport.ocrQuotes]); the same
 * page when nothing changes.
 */
fun OcrPage.inLanguage(language: Language): OcrPage {
    val support = language.support
    val texts = support.ocrQuotes(support.ocrLines(paragraphs.flatMap { it.lines }.map { line -> line.words.map { it.text } }))
    var changed = false
    var lineIndex = 0
    val fixed = paragraphs.map { paragraph ->
        paragraph.copy(
            lines = paragraph.lines.map { line ->
                val lineTexts = texts[lineIndex++]
                line.copy(
                    words = line.words.mapIndexed { index, word ->
                        val text = lineTexts[index]
                        if (text == word.text) {
                            word
                        } else {
                            changed = true
                            word.copy(text = text, characterBoxes = word.characterBoxes?.let { joinedBoxes(word.text, text, it) })
                        }
                    },
                )
            },
        )
    }
    return if (changed) copy(paragraphs = fixed) else this
}

/**
 * The character boxes of [old] for [new], which writes each character of [old] as one, or two of them as one (>> as
 * »): their boxes joined. Null when they do not line up.
 */
private fun joinedBoxes(old: String, new: String, boxes: List<Box>): List<Box>? {
    val from = old.codePoints().toArray()
    val to = new.codePoints().toArray()
    if (boxes.size != from.size) return null
    if (from.size == to.size) return boxes
    val result = ArrayList<Box>(to.size)
    var index = 0
    for (position in to.indices) {
        if (index >= from.size) return null
        val extra = (from.size - index) - (to.size - position)
        if (extra > 0 && from[index] != to[position]) {
            result += boxes[index].union(boxes[index + 1])
            index += 2
        } else {
            result += boxes[index++]
        }
    }
    return result.takeIf { index == from.size }
}

/**
 * Adds the lines of [other] that do not overlap any line already on this page, keeping their paragraphs. Used to fill
 * gaps: OCR around app text, or a band recognized again for small text. Both pages must use the same coordinates.
 */
fun OcrPage.withMissingFrom(other: OcrPage): OcrPage {
    val existing = paragraphs.flatMap { paragraph -> paragraph.lines.map { it.box } }
    val added = other.paragraphs.mapNotNull { paragraph ->
        paragraph.lines
            .filter { line -> existing.none { overlaps(it, line.box) } }
            .takeIf { it.isNotEmpty() }
            ?.let { OcrParagraph(it, paragraph.engine ?: other.engine) }
    }
    return if (added.isEmpty()) this else copy(paragraphs = paragraphs + added)
}

/** Boxes overlap when their intersection covers a third of the smaller one; touching neighbours do not. */
private fun overlaps(a: Box, b: Box): Boolean {
    val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    if (width <= 0f || height <= 0f) return false
    val smaller = minOf(a.width * a.height, b.width * b.height)
    return smaller > 0f && width * height >= smaller * OVERLAP_FRACTION
}

private const val OVERLAP_FRACTION = 1f / 3f

/** @property engine where this paragraph came from when it differs from its page, e.g. OCR added to app text. */
data class OcrParagraph(val lines: List<OcrLine>, val engine: OcrEngineType? = null) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * @property vertical true for top-to-bottom text (Japanese tategaki).
 * @property rightToLeft a horizontal line written from right to left (Arabic, Hebrew); its words are in reading order,
 *   so they run from right to left on the screen.
 */
data class OcrLine(
    val words: List<OcrWord>,
    val box: Box,
    val vertical: Boolean,
    val rightToLeft: Boolean = false,
) {
    val text: String get() = words.joinToString("") { it.text + it.separator }.trim()
}

/**
 * @property separator text between this word and the next one; empty for Japanese.
 * @property characterBoxes per-character boxes when the engine provides them, otherwise null.
 * @property engine the engine that read this word, set on every word of an assembled photo; null elsewhere, where
 *   the paragraph's or the page's engine applies.
 */
data class OcrWord(
    val text: String,
    val separator: String,
    val box: Box,
    val characterBoxes: List<Box>? = null,
    val engine: OcrEngineType? = null,
)
