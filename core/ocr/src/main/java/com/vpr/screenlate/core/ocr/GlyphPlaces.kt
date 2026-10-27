package com.vpr.screenlate.core.ocr

/**
 * Whether [codePoint], after [previous], takes a place of its own along a line: a mark shares the place of the glyph
 * before it, and so does a Khmer consonant written below the one before it.
 */
internal fun takesPlace(codePoint: Int, previous: Int?): Boolean =
    previous == null || !(isNonSpacingMark(codePoint) || previous == KHMER_COENG)

/** The places along a line that [codePoints] take from [from] until [until]. */
internal fun places(codePoints: IntArray, from: Int = 0, until: Int = codePoints.size): Int =
    (from until until).count { takesPlace(codePoints[it], codePoints.getOrNull(it - 1)) }

private fun isNonSpacingMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
    Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

/** The Khmer sign that writes the next consonant below the one before it. */
private const val KHMER_COENG = 0x17D2
