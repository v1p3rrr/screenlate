package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/** Immutable bounding-volume tree. Query results retain input order, including touching and zero-area boxes. */
internal class BoxIndex(private val boxes: List<Box>) {
    private class Node(val bounds: Box, val entries: List<Int>, val left: Node? = null, val right: Node? = null)
    private val root = build(boxes.indices.filter { boxes[it].valid() })

    fun query(area: Box): List<Int> {
        if (!area.valid()) return emptyList()
        val found = mutableListOf<Int>()
        fun visit(node: Node?) {
            if (node == null || !node.bounds.touches(area)) return
            if (node.left == null) {
                node.entries.filterTo(found) { boxes[it].touches(area) }
            } else {
                visit(node.left)
                visit(node.right)
            }
        }
        visit(root)
        found.sort()
        return found
    }

    private fun build(entries: List<Int>): Node? {
        val bounds = Box.unionOf(entries.map(boxes::get)) ?: return null
        if (entries.size <= 12) return Node(bounds, entries)
        val sorted = if (bounds.width >= bounds.height) entries.sortedBy { boxes[it].centerX }
            else entries.sortedBy { boxes[it].centerY }
        val middle = sorted.size / 2
        return Node(bounds, emptyList(), build(sorted.subList(0, middle)), build(sorted.subList(middle, sorted.size)))
    }

    private fun Box.valid() = !left.isNaN() && !top.isNaN() && !right.isNaN() && !bottom.isNaN() &&
        left <= right && top <= bottom
    private fun Box.touches(other: Box) =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
}
