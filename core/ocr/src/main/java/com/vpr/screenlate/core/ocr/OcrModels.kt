package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

enum class OcrEngineType {
    LENS,
    ML_KIT,

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
    fun offset(dx: Float, dy: Float): OcrPage = copy(
        paragraphs = paragraphs.map { paragraph ->
            paragraph.copy(
                lines = paragraph.lines.map { line ->
                    line.copy(
                        box = line.box.offset(dx, dy),
                        words = line.words.map { word ->
                            word.copy(
                                box = word.box.offset(dx, dy),
                                characterBoxes = word.characterBoxes?.map { it.offset(dx, dy) },
                            )
                        },
                    )
                },
            )
        },
    )
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
 */
data class OcrLine(
    val words: List<OcrWord>,
    val box: Box,
    val vertical: Boolean,
) {
    val text: String get() = words.joinToString("") { it.text + it.separator }.trim()
}

/**
 * @property separator text between this word and the next one; empty for Japanese.
 * @property characterBoxes per-character boxes when the engine provides them, otherwise null.
 */
data class OcrWord(
    val text: String,
    val separator: String,
    val box: Box,
    val characterBoxes: List<Box>? = null,
)
