package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/**
 * Puts recognized paragraphs into reading order, so that a word broken at the end of a column is read on in the next
 * one.
 *
 * Lens lists the columns of a vertical paragraph left to right, and it makes one paragraph of every column when the
 * gap between columns is wide. Columns are therefore sorted right to left, and a vertical paragraph whose last column
 * runs to the bottom of the text is joined with a paragraph that starts right after it in the next column, at the same
 * top margin and in the same text size. Horizontal text keeps the engine's paragraphs: Lens already groups wrapped lines
 * even at twice the usual line spacing, while its separate horizontal paragraphs are different things (a post's header
 * and its text, the labels of a score table) that must not run together. Text read from apps is left alone too: an
 * app's text node already holds its wrapped lines.
 */
internal object ReadingOrder {

    fun paragraphs(page: OcrPage): List<OcrParagraph> {
        val paragraphs = page.paragraphs.map(::withColumnsInOrder)
        // A paragraph without its own engine has the page's: lines added to a page carry the engine of their source.
        val engines = paragraphs.map { it.engine ?: page.engine }
        val next = IntArray(paragraphs.size) { -1 }
        val continued = BooleanArray(paragraphs.size)
        for (index in paragraphs.indices) {
            if (engines[index] == OcrEngineType.ACCESSIBILITY) continue
            next[index] = paragraphs.indices
                .filter { other -> other != index && !continued[other] && engines[other] == engines[index] }
                .mapNotNull { other -> gapToContinuation(paragraphs[index], paragraphs[other])?.let { other to it } }
                .minByOrNull { (_, gap) -> gap }
                ?.first
                ?: continue
            continued[next[index]] = true
        }
        val used = BooleanArray(paragraphs.size)
        return paragraphs.indices.filter { !continued[it] }.plus(paragraphs.indices).mapNotNull { start ->
            if (used[start]) return@mapNotNull null
            val lines = mutableListOf<OcrLine>()
            var index = start
            while (index >= 0 && !used[index]) {
                used[index] = true
                lines += paragraphs[index].lines
                index = next[index]
            }
            OcrParagraph(lines, paragraphs[start].engine)
        }
    }

    /** Columns of a vertical paragraph from right to left; horizontal paragraphs keep the engine's order. */
    private fun withColumnsInOrder(paragraph: OcrParagraph): OcrParagraph {
        val lines = paragraph.lines
        if (lines.size < 2 || lines.none { it.vertical } || !lines.all { it.runs(vertical = true) }) return paragraph
        return paragraph.copy(lines = lines.sortedByDescending { it.box.centerX })
    }

    /**
     * How far [next] starts from the last column of [paragraph], in pixels, when [next] goes on with its text; null when
     * it does not.
     */
    private fun gapToContinuation(paragraph: OcrParagraph, next: OcrParagraph): Float? {
        val last = paragraph.lines.lastOrNull() ?: return null
        val first = next.lines.firstOrNull() ?: return null
        if (!last.vertical || !(paragraph.lines + next.lines).all { it.runs(vertical = true) }) return null
        val size = last.box.width
        if (size <= 0f || first.box.width !in size * MIN_SIZE_RATIO..size / MIN_SIZE_RATIO) return null
        val gap = last.box.left - first.box.right
        val margin = paragraph.lines.minOf { it.box.top }
        val end = (paragraph.lines + next.lines).maxOf { it.box.bottom }
        return gap.takeIf {
            first.box.centerX < last.box.centerX && gap in -size * MAX_OVERLAP..size * MAX_GAP &&
                first.box.top in margin - size * MAX_INDENT..margin + size * MAX_MARGIN_SHIFT &&
                last.box.bottom >= end - size * MAX_SHORTFALL
        }
    }

    /** True when the line runs in that direction; a single character fits either. */
    private fun OcrLine.runs(vertical: Boolean): Boolean =
        this.vertical == vertical || words.sumOf { it.text.codePointCount(0, it.text.length) } <= 1

    /** Columns of one text differ in width by less than this. */
    private const val MIN_SIZE_RATIO = 0.75f

    /** Gap between the columns, in column widths. */
    private const val MAX_GAP = 1.5f
    private const val MAX_OVERLAP = 0.25f

    /** The next column starts at the top margin, or up to this far above it when the paragraph began indented. */
    private const val MAX_INDENT = 1.5f
    private const val MAX_MARGIN_SHIFT = 0.5f

    /** The broken column ends at most this many characters above the text's bottom. */
    private const val MAX_SHORTFALL = 1.5f
}
