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

    /** The visible text of application windows, or null when none of it has character positions. */
    fun read(screenWidth: Int, screenHeight: Int): OcrPage? {
        val started = SystemClock.elapsedRealtime()
        val paragraphs = mutableListOf<OcrParagraph>()
        val screen = Rect(0, 0, screenWidth, screenHeight)
        stats = Stats()
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            collect(root, screen, paragraphs, depth = 0)
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

    private fun collect(node: AccessibilityNodeInfo, screen: Rect, out: MutableList<OcrParagraph>, depth: Int) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            stats.withText++
            paragraph(node, text, screen)?.let(out::add)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collect(child, screen, out, depth + 1)
        }
    }

    private fun paragraph(node: AccessibilityNodeInfo, text: String, screen: Rect): OcrParagraph? {
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

        // One entry per code point; characters without a visible location end the current line.
        val lines = mutableListOf<MutableList<Pair<String, Box>>>()
        var current = mutableListOf<Pair<String, Box>>()
        var index = 0
        while (index < length) {
            val next = text.offsetByCodePoints(index, 1).coerceAtMost(text.length)
            val character = text.substring(index, next)
            val rect = locations.getOrNull(index) as? RectF
            index = next
            if (rect == null || rect.isEmpty || !screen.contains(rect.centerX().toInt(), rect.centerY().toInt())) {
                if (current.isNotEmpty()) lines += current.also { current = mutableListOf() }
                continue
            }
            val box = Box(rect.left, rect.top, rect.right, rect.bottom)
            val previous = current.lastOrNull()?.second
            // A character that starts left of the previous one or below it begins a new line.
            if (previous != null && (box.left < previous.left - previous.width / 2 || box.top >= previous.bottom)) {
                lines += current.also { current = mutableListOf() }
            }
            current += character to box
        }
        if (current.isNotEmpty()) lines += current
        if (lines.isEmpty()) return null
        if (node.isEditable) {
            val bounds = Rect().also(node::getBoundsInScreen)
            val field = Box(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
            if (startsAtCorner(field, lines.first().first().second)) {
                stats.fieldsAtCorner++
                return null
            }
        }
        if (sharesBoxes(lines.flatten().map { it.second })) {
            stats.sharedBoxes++
            return null
        }
        return OcrParagraph(
            lines.map { characters ->
                val box = Box.unionOf(characters.map { it.second })!!
                val word = OcrWord(
                    text = characters.joinToString("") { it.first },
                    separator = "",
                    box = box,
                    characterBoxes = characters.map { it.second },
                )
                OcrLine(listOf(word), box, vertical = false)
            },
        )
    }

    private companion object {
        const val TAG = "AccessibilityText"
        const val MAX_DEPTH = 64
        const val MAX_CHARACTERS = 2000
    }
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
