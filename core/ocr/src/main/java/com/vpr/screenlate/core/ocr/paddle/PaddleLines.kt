package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.abs

/** Combines detector fragments before their pixels are cropped and recognized. */
internal object PaddleLines {
    fun combine(lines: List<DetectedLine>, bounds: Box): List<DetectedLine> {
        var current = lines
        while (true) {
            val combined = combineOnce(current, bounds)
            if (combined.size == current.size) return combined
            current = combined
        }
    }

    private fun combineOnce(lines: List<DetectedLine>, bounds: Box): List<DetectedLine> {
        val distinct = withoutContained(lines)
        val groups = IntArray(distinct.size) { it }
        fun root(value: Int): Int {
            var at = value
            while (groups[at] != at) at = groups[at]
            return at
        }
        for (i in distinct.indices) for (j in 0 until i) {
            if (sameRow(distinct[i].area, distinct[j].area)) groups[root(i)] = root(j)
        }
        val combined = distinct.indices.groupBy(::root).values.map { members ->
            if (members.size == 1) return@map distinct[members.single()]
            val areas = members.map { distinct[it].area }
            val area = enclosing(areas)
            val weight = members.sumOf { distinct[it].area.size().toDouble() }
            val score = members.sumOf { distinct[it].score * distinct[it].area.size().toDouble() } / weight
            DetectedLine(area, area.bounds().within(bounds), score.toFloat())
        }
        // A small island may straddle two fragments and only become contained after they join.
        return withoutContained(combined).sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    private fun withoutContained(lines: List<DetectedLine>): List<DetectedLine> = lines.filterIndexed { i, line ->
        lines.indices.none { j ->
            val other = lines[j]
            j != i && (other.area.size() > line.area.size() || j < i && other.area.size() == line.area.size()) &&
                other.area.contains(line.area)
        }
    }

    private fun sameRow(first: OrientedBox, second: OrientedBox): Boolean {
        // Long columns already have their own reading-order path. Combining their crops did not improve accuracy.
        if (first.thickness >= PaddlePipeline.VERTICAL_RATIO * first.length ||
            second.thickness >= PaddlePipeline.VERTICAL_RATIO * second.length) return false
        val a = if (first.size() >= second.size()) first else second
        val b = if (a === first) second else first
        // Two single glyphs have no reliable reading direction; they may belong to adjacent vertical columns.
        if (maxOf(a.length / a.thickness, b.length / b.thickness) < PaddlePipeline.VERTICAL_RATIO) return false
        if (a.alongX * b.alongX + a.alongY * b.alongY < MIN_DIRECTION) return false
        val size = maxOf(a.thickness, b.thickness)
        if (size <= 0f || minOf(a.thickness, b.thickness) < size * MIN_SIZE_RATIO) return false
        val dx = b.x + b.alongX * b.length / 2 + b.acrossX * b.thickness / 2 -
            (a.x + a.alongX * a.length / 2 + a.acrossX * a.thickness / 2)
        val dy = b.y + b.alongY * b.length / 2 + b.acrossY * b.thickness / 2 -
            (a.y + a.alongY * a.length / 2 + a.acrossY * a.thickness / 2)
        if (abs(dx * a.acrossX + dy * a.acrossY) > minOf(a.thickness, b.thickness) * MAX_CENTER_SHIFT) return false
        val gap = abs(dx * a.alongX + dy * a.alongY) - (a.length + b.length) / 2
        // A detached compact shape taller than its text is commonly an icon, not a letter fragment.
        val firstCompact = a.length < a.thickness * PaddlePipeline.VERTICAL_RATIO && a.thickness > b.thickness * MAX_COMPACT_HEIGHT
        val secondCompact = b.length < b.thickness * PaddlePipeline.VERTICAL_RATIO && b.thickness > a.thickness * MAX_COMPACT_HEIGHT
        if (gap > 0f && (firstCompact || secondCompact)) return false
        return gap <= size * MAX_GAP
    }

    private fun OrientedBox.size() = length * thickness

    /** Keep the largest fragment's baseline; the hull's shortest edge can tilt it towards a small neighbour. */
    private fun enclosing(areas: List<OrientedBox>): OrientedBox {
        val direction = areas.maxBy { it.size() }
        val points = areas.flatMap { it.corners().asIterable() }
        val along = (points.indices step 2).map { points[it] * direction.alongX + points[it + 1] * direction.alongY }
        val across = (points.indices step 2).map { points[it] * direction.acrossX + points[it + 1] * direction.acrossY }
        return OrientedBox(
            along.min() * direction.alongX + across.min() * direction.acrossX,
            along.min() * direction.alongY + across.min() * direction.acrossY,
            direction.alongX, direction.alongY, along.max() - along.min(), across.max() - across.min(),
        )
    }

    private fun OrientedBox.corners() = floatArrayOf(
        x, y, x + alongX * length, y + alongY * length,
        x + acrossX * thickness, y + acrossY * thickness,
        x + alongX * length + acrossX * thickness, y + alongY * length + acrossY * thickness,
    )

    private fun OrientedBox.contains(other: OrientedBox): Boolean {
        val points = other.corners()
        return (points.indices step 2).all { i ->
            val dx = points[i] - x
            val dy = points[i + 1] - y
            dx * alongX + dy * alongY in -0.5f..length + 0.5f &&
                dx * acrossX + dy * acrossY in -0.5f..thickness + 0.5f
        }
    }

    private const val MIN_DIRECTION = 0.985f
    private const val MIN_SIZE_RATIO = 0.5f
    private const val MAX_CENTER_SHIFT = 0.2f
    private const val MAX_GAP = 0.5f
    private const val MAX_COMPACT_HEIGHT = 1.1f
}
