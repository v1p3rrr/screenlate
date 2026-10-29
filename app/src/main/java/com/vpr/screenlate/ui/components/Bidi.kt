package com.vpr.screenlate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val RIGHT_TO_LEFT_ISOLATE = '\u2067'
private const val POP_ISOLATE = '\u2069'

/**
 * [text] that is not in the interface language (a dictionary's own description, a catalog text that falls back to
 * English) marked so that it keeps its own direction: in a right-to-left interface an English sentence keeps its
 * full stop at its end. A line whose first letter has the interface's direction stays as it is. Each line is marked
 * on its own, since a line break ends the marking.
 */
fun keepDirection(text: String, rightToLeft: Boolean): String = text.split('\n').joinToString("\n") { line ->
    when (firstStrongRightToLeft(line)) {
        null, rightToLeft -> line
        true -> RIGHT_TO_LEFT_ISOLATE + line + POP_ISOLATE
        false -> LEFT_TO_RIGHT_ISOLATE + line + POP_ISOLATE
    }
}

/** Whether the first letter with a direction is right-to-left; null when [line] has none. */
private fun firstStrongRightToLeft(line: String): Boolean? {
    for (point in line.codePoints().toArray()) {
        when (Character.getDirectionality(point)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
        }
    }
    return null
}

/** [keepDirection] for the screen's layout direction. */
@Composable
fun ownDirection(text: String): String {
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(text, rightToLeft) { keepDirection(text, rightToLeft) }
}
