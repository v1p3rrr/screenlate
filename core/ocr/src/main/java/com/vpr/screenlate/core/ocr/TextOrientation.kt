package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/**
 * Heuristic shared by all engines: a line of at least two characters that is clearly taller than wide is vertical.
 * Single characters are ambiguous and treated as horizontal.
 */
internal fun isVerticalLine(box: Box, text: String): Boolean =
    text.codePointCount(0, text.length) >= 2 && box.height > box.width * 1.2f

/** Whether [text] reads from right to left: its first letter of a strong direction is Arabic or Hebrew-like. */
internal fun isRightToLeftText(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
        index += Character.charCount(codePoint)
    }
    return false
}
