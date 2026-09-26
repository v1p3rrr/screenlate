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
            OcrParagraph(
                paragraph.lines.map { line ->
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

data class OcrParagraph(val lines: List<OcrLine>) {
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
