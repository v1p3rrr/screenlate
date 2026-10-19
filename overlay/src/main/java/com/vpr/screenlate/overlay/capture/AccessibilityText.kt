package com.vpr.screenlate.overlay.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.os.BundleCompat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import kotlin.math.abs

/**
 * Reads the text of the app windows on screen from the accessibility node tree, with per-character positions
 * (`EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY`), and shapes it like an OCR result in screen coordinates.
 *
 * Only nodes that report character positions are used: without them the aim cannot be matched to a character.
 */
class AccessibilityText(private val service: AccessibilityService) {

    /**
     * The visible text of application windows, or null when none of it has character positions.
     *
     * @param separator the language's word separator, put between lines that a space or line break wraps.
     */
    fun read(screenWidth: Int, screenHeight: Int, separator: String): OcrPage? {
        val started = SystemClock.elapsedRealtime()
        val paragraphs = mutableListOf<OcrParagraph>()
        val screen = Rect(0, 0, screenWidth, screenHeight)
        stats = Stats()
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            collect(root, screen, separator, paragraphs, depth = 0)
        }
        Log.d(
            TAG,
            "App text: ${paragraphs.size} of ${stats.withText} text nodes in ${SystemClock.elapsedRealtime() - started} ms; " +
                "${stats.withoutPositions} without character positions, ${stats.sharedBoxes} with one box for all, " +
                "${stats.fieldsAtCorner} fields with boxes at the corner",
        )
        if (paragraphs.isEmpty()) return null
        return OcrPage(screenWidth, screenHeight, paragraphs, OcrEngineType.ACCESSIBILITY)
    }

    /** What the last [read] found, for the log. */
    private class Stats {
        var withText = 0
        var withoutPositions = 0
        var sharedBoxes = 0
        var fieldsAtCorner = 0
    }

    private var stats = Stats()

    private fun collect(
        node: AccessibilityNodeInfo,
        screen: Rect,
        separator: String,
        out: MutableList<OcrParagraph>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            stats.withText++
            paragraph(node, text, screen, separator)?.let(out::add)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collect(child, screen, separator, out, depth + 1)
        }
    }

    private fun paragraph(node: AccessibilityNodeInfo, text: String, screen: Rect, separator: String): OcrParagraph? {
        if (AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY !in node.availableExtraData) {
            stats.withoutPositions++
            return null
        }
        val length = minOf(text.length, MAX_CHARACTERS)
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, length)
        }
        if (!node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, arguments)) return null
        val locations = BundleCompat.getParcelableArray(
            node.extras,
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            RectF::class.java,
        ) ?: return null

        // One entry per code point, with its box where it is visible on screen.
        val characters = ArrayList<Pair<String, Box?>>(length)
        var index = 0
        while (index < length) {
            val next = text.offsetByCodePoints(index, 1).coerceAtMost(text.length)
            val rect = (locations.getOrNull(index) as? RectF)
                ?.takeIf { !it.isEmpty && screen.contains(it.centerX().toInt(), it.centerY().toInt()) }
            characters += text.substring(index, next) to rect?.let { Box(it.left, it.top, it.right, it.bottom) }
            index = next
        }
        val lines = appTextLines(characters, separator)
        if (lines.isEmpty()) return null
        if (node.isEditable) {
            val bounds = Rect().also(node::getBoundsInScreen)
            val field = Box(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
            if (startsAtCorner(field, lines.first().characterBoxes!!.first())) {
                stats.fieldsAtCorner++
                return null
            }
        }
        if (sharesBoxes(lines.flatMap { it.characterBoxes!! })) {
            stats.sharedBoxes++
            return null
        }
        return OcrParagraph(lines.map { OcrLine(listOf(it), it.box, vertical = false) })
    }

    private companion object {
        const val TAG = "AccessibilityText"
        const val MAX_DEPTH = 64
        const val MAX_CHARACTERS = 2000
    }
}

/**
 * A node's text as lines, one word each, from its code points and their boxes on screen (null where a character has no
 * visible location). A character without a location ends the line, and so does one that starts left of the previous
 * one or below it. Where a space or line break without a location ends it, the line takes [separator], so the words
 * on both sides of a wrap stay apart in a language written with spaces. A combining mark typed apart (a stress or tone
 * mark: ко́шка, hoà) has an empty box in a text view; it stays with its letter and takes the letter's box.
 */
internal fun appTextLines(characters: List<Pair<String, Box?>>, separator: String): List<OcrWord> {
    val lines = mutableListOf<OcrWord>()
    var current = mutableListOf<Pair<String, Box>>()
    fun endLine(spaced: Boolean) {
        if (current.isEmpty()) return
        lines += OcrWord(
            text = current.joinToString("") { it.first },
            separator = if (spaced) separator else "",
            box = Box.unionOf(current.map { it.second })!!,
            characterBoxes = current.map { it.second },
        )
        current = mutableListOf()
    }
    // Characters without a location since the last visible one, and whether a space or line break was among them.
    var broken = false
    var spaced = false
    for ((character, box) in characters) {
        val previous = current.lastOrNull()?.second
        if (box == null && previous != null && !broken && isCombiningMark(character)) {
            current += character to previous
            continue
        }
        if (box == null) {
            broken = true
            if (character.isBlank()) spaced = true
            continue
        }
        if (broken || previous != null && (box.left < previous.left - previous.width / 2 || box.top >= previous.bottom)) {
            endLine(spaced)
        }
        broken = false
        spaced = false
        current += character to box
    }
    endLine(spaced)
    return lines
}

private fun isCombiningMark(character: String): Boolean = when (Character.getType(character.codePointAt(0))) {
    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

/**
 * True when most characters report the same box as the one before them. Some views (a WebView without laid-out text
 * runs, for one) answer with the whole node's bounds for every character, which cannot place the aim on a character;
 * such text is left to OCR.
 */
internal fun sharesBoxes(boxes: List<Box>): Boolean {
    if (boxes.size < 2) return false
    val repeated = boxes.zipWithNext().count { (previous, box) -> previous == box }
    return repeated * 2 >= boxes.size - 1
}

/**
 * True when a text field's first character box sits exactly at the field's top-left corner. Compose text fields place
 * the character boxes relative to the whole field, padding included, so every box lands up and left of its glyph by
 * the padding; such text is left to OCR. A field without padding matches too and is read by OCR as well, which only
 * costs the exactness of app text.
 */
internal fun startsAtCorner(field: Box, first: Box): Boolean =
    abs(first.left - field.left) < CORNER_TOLERANCE && abs(first.top - field.top) < CORNER_TOLERANCE

private const val CORNER_TOLERANCE = 1f
