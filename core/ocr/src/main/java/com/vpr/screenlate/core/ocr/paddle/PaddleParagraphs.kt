package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrParagraph
import kotlin.math.abs

/** Paragraphs formed from the detector's lines; other engines keep their own paragraph boundaries. */
internal object PaddleParagraphs {
    fun from(lines: List<OcrLine>): List<OcrParagraph> {
        val paragraphs = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))) {
            val previous = paragraphs.filter { continues(it.last(), line) }
                .minByOrNull { line.box.top - it.last().box.bottom }
            if (previous == null) paragraphs += mutableListOf(line) else previous += line
        }
        return paragraphs.map { OcrParagraph(it.toList()) }
    }

    private fun continues(previous: OcrLine, next: OcrLine): Boolean {
        // Vertical columns use ReadingOrder's continuation checks after recognition, including their text size.
        if (previous.vertical || next.vertical || previous.rightToLeft != next.rightToLeft) return false
        val a = previous.box
        val b = next.box
        val previousHeight = previous.textHeight()
        val nextHeight = next.textHeight()
        val size = minOf(previousHeight, nextHeight)
        if (size <= 0f || size < maxOf(previousHeight, nextHeight) * MIN_SIZE_RATIO) return false
        val gap = b.centerY - a.centerY - (previousHeight + nextHeight) / 2
        if (b.centerY <= a.centerY || gap !in -size * MAX_OVERLAP..size * MAX_GAP) return false
        if (minOf(a.right, b.right) - maxOf(a.left, b.left) < minOf(a.width, b.width) * MIN_WIDTH_OVERLAP) return false
        return minOf(abs(a.left - b.left), abs(a.right - b.right)) <= size * MAX_MARGIN_SHIFT
    }

    /** A long tilted crop has a taller axis-aligned box; individual characters retain its text size. */
    private fun OcrLine.textHeight(): Float {
        val heights = words.flatMap { it.characterBoxes ?: listOf(it.box) }.map { it.height }.filter { it > 0f }.sorted()
        return heights.getOrNull(heights.size / 2)?.coerceAtMost(box.height) ?: box.height
    }

    private const val MIN_SIZE_RATIO = 0.7f
    private const val MIN_WIDTH_OVERLAP = 0.5f
    private const val MAX_OVERLAP = 0.2f
    private const val MAX_GAP = 0.6f
    private const val MAX_MARGIN_SHIFT = 0.75f
}
