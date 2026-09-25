package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/**
 * Heuristic shared by all engines: a line of at least two characters that is clearly taller than wide is vertical.
 * Single characters are ambiguous and treated as horizontal.
 */
internal fun isVerticalLine(box: Box, text: String): Boolean =
    text.codePointCount(0, text.length) >= 2 && box.height > box.width * 1.2f
