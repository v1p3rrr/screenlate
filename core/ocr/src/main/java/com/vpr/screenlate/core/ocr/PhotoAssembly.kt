package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
import java.text.Normalizer
import kotlin.math.abs

/**
 * Joins tile-local observations in original-image coordinates. Collinear fragments join across tile overlaps;
 * paragraph membership follows their original paragraphs. Word suppression requires geometric overlap, never text
 * identity alone, and prefers observations away from artificial crop edges. Character boxes and provenance survive.
 */
object PhotoAssembly {
    fun assemble(width: Int, height: Int, language: Language, tiles: List<PhotoTilePage>): OcrPage {
        require(width > 0 && height > 0)
        var paragraphCount = 0
        val lines = buildList {
            tiles.forEachIndexed { tileIndex, result ->
                require(result.tile.right <= width && result.tile.bottom <= height)
                val placed = result.page.offset(result.tile.left.toFloat(), result.tile.top.toFloat())
                placed.paragraphs.forEach { paragraph ->
                    val paragraphId = paragraphCount++
                    paragraph.lines.filter { it.words.isNotEmpty() }.forEach { line ->
                        add(Observation(line.copy(words = line.words.map { word ->
                            word.copy(engine = word.engine ?: paragraph.engine ?: result.page.engine)
                        }), result.tile, tileIndex, paragraphId))
                    }
                }
            }
        }
        val lineGroups = Groups(lines.size)
        val paragraphs = Groups(paragraphCount)
        val index = BoxIndex(lines.map { it.line.box })
        for (i in lines.indices) {
            for (j in index.query(lines[i].line.box)) {
                if (j <= i || !sameLine(lines[i], lines[j])) continue
                lineGroups.join(i, j)
                paragraphs.join(lines[i].paragraph, lines[j].paragraph)
            }
        }
        val merged = lines.indices.groupBy(lineGroups::root).values.map { members ->
            val observations = members.map(lines::get)
            paragraphs.root(observations.first().paragraph) to mergeLine(observations, width, height, language)
        }
        val assembled = merged.groupBy({ paragraphs.root(it.first) }, { it.second }).values.map { paragraphLines ->
            val vertical = paragraphLines.all { it.vertical }
            val ordered = if (vertical) paragraphLines.sortedByDescending { it.box.centerX }
                else paragraphLines.sortedWith(compareBy<OcrLine> { it.box.top }.thenBy { it.box.left })
            val spaced = ordered.mapIndexed { index, line ->
                val last = line.words.lastOrNull()
                if (index < ordered.lastIndex && last != null && last.separator.isEmpty()) {
                    line.copy(words = line.words.dropLast(1) + last.copy(separator = language.support.wordSeparator))
                } else line
            }
            val engines = spaced.flatMap { it.words }.mapNotNull { it.engine }.distinct()
            OcrParagraph(spaced, engines.singleOrNull())
        }.sortedWith(compareBy<OcrParagraph> { it.lines.minOf { line -> line.box.top } }
            .thenBy { it.lines.minOf { line -> line.box.left } })
        val engine = tiles.firstOrNull { it.page.engine == OcrEngineType.LENS }?.page?.engine
            ?: tiles.firstOrNull()?.page?.engine ?: OcrEngineType.LENS
        return OcrPage(width, height, assembled, engine).inLanguage(language)
    }

    private data class Observation(val line: OcrLine, val tile: PhotoTile, val tileIndex: Int, val paragraph: Int)
    private data class Word(val word: OcrWord, val observation: Observation)

    private fun sameLine(a: Observation, b: Observation): Boolean {
        if (a.tileIndex == b.tileIndex || !a.tile.box.intersects(b.tile.box)) return false
        val x = a.line
        val y = b.line
        if (x.vertical != y.vertical || x.rightToLeft != y.rightToLeft) return false
        val sizeA = if (x.vertical) x.box.width else x.box.height
        val sizeB = if (y.vertical) y.box.width else y.box.height
        if (minOf(sizeA, sizeB) <= 0f || minOf(sizeA, sizeB) < maxOf(sizeA, sizeB) * 0.5f) return false
        val crossOverlap = if (x.vertical) minOf(x.box.right, y.box.right) - maxOf(x.box.left, y.box.left)
            else minOf(x.box.bottom, y.box.bottom) - maxOf(x.box.top, y.box.top)
        val centerGap = if (x.vertical) abs(x.box.centerX - y.box.centerX) else abs(x.box.centerY - y.box.centerY)
        if (crossOverlap < minOf(sizeA, sizeB) * 0.65f || centerGap > maxOf(sizeA, sizeB) * 0.4f) return false
        // A shared physical word anchors the merge even if the engines grouped the rest of the line differently.
        if (x.words.any { first -> y.words.any { second -> overlap(first.box, second.box) >= 0.5f } }) return true
        val alongOverlap = if (x.vertical) minOf(x.box.bottom, y.box.bottom) - maxOf(x.box.top, y.box.top)
            else minOf(x.box.right, y.box.right) - maxOf(x.box.left, y.box.left)
        return alongOverlap > 0f
    }

    private fun mergeLine(observations: List<Observation>, width: Int, height: Int, language: Language): OcrLine {
        val example = observations.first().line
        val candidates = observations.flatMap { observation -> observation.line.words.map { Word(it, observation) } }
        val index = BoxIndex(candidates.map { it.word.box })
        val owner = IntArray(candidates.size) { -1 }
        val chosen = mutableListOf<Int>()
        val priority = candidates.indices.sortedWith(compareByDescending<Int> {
            edgeMargin(candidates[it], width, height) > 3f
        }.thenByDescending { edgeMargin(candidates[it], width, height) }
            .thenByDescending { candidates[it].word.characterBoxes != null })
        for (i in priority) {
            if (owner[i] >= 0) continue
            owner[i] = i
            chosen += i
            for (j in index.query(candidates[i].word.box)) {
                if (owner[j] >= 0 || candidates[i].observation.tileIndex == candidates[j].observation.tileIndex) continue
                if (duplicates(candidates[i], candidates[j], width, height)) owner[j] = i
            }
        }
        // Keep each engine's logical word order (including Latin runs inside RTL lines). Geometry only orders
        // fragments whose original observations give no ordering constraint.
        val edges = chosen.associateWith { mutableSetOf<Int>() }
        var offset = 0
        for (observation in observations) {
            val members = (offset until offset + observation.line.words.size).map { owner[it] }.distinct()
            members.zipWithNext().forEach { (from, to) -> if (from != to) edges.getValue(from).add(to) }
            offset += observation.line.words.size
        }
        val pending = chosen.toMutableSet()
        val sorted = buildList {
            while (pending.isNotEmpty()) {
                val free = pending.filter { target -> pending.none { target in edges.getValue(it) } }
                val next = (free.ifEmpty { pending.toList() }).minBy { along(candidates[it].word.box, example) }
                add(candidates[next])
                pending.remove(next)
            }
        }
        val stitched = mutableListOf<Word>()
        for (candidate in sorted) {
            val previous = stitched.lastOrNull()
            val joined = previous?.let { stitch(it, candidate, width, height) }
            if (joined != null) stitched[stitched.lastIndex] = joined else stitched += candidate
        }
        val words = stitched.mapIndexed { i, candidate ->
            val word = candidate.word
            // At an artificial tile edge an engine may omit a word separator. Use the language's rule only for
            // physically separated words; never change an explicit separator or split a combining sequence.
            val next = stitched.getOrNull(i + 1)?.word
            val gap = next?.let { gap(word.box, it.box, example) } ?: 0f
            val size = if (example.vertical) word.box.width else word.box.height
            if (word.separator.isEmpty() && gap > size * 0.08f) word.copy(separator = language.support.wordSeparator) else word
        }
        return example.copy(words = words, box = Box.unionOf(words.map { it.box }) ?: example.box)
    }

    /** Very long words can be clipped in both tiles; join only a geometrically consistent shared suffix/prefix. */
    private fun stitch(a: Word, b: Word, width: Int, height: Int): Word? {
        if (a.observation.tileIndex == b.observation.tileIndex) return null
        val line = a.observation.line
        val tileA = a.observation.tile
        val tileB = b.observation.tile
        val boxA = a.word.box
        val boxB = b.word.box
        val endClipped = when {
            line.vertical -> tileA.bottom < height && abs(boxA.bottom - tileA.bottom) <= 3
            line.rightToLeft -> tileA.left > 0 && abs(boxA.left - tileA.left) <= 3
            else -> tileA.right < width && abs(boxA.right - tileA.right) <= 3
        }
        val startClipped = when {
            line.vertical -> tileB.top > 0 && abs(boxB.top - tileB.top) <= 3
            line.rightToLeft -> tileB.right < width && abs(boxB.right - tileB.right) <= 3
            else -> tileB.left > 0 && abs(boxB.left - tileB.left) <= 3
        }
        if (!endClipped || !startClipped) return null
        val first = a.word.text.codePoints().toArray()
        val second = b.word.text.codePoints().toArray()
        val common = (minOf(first.size, second.size) downTo 1).firstOrNull { count ->
            first.takeLast(count) == second.take(count)
        } ?: return null
        val sharedLength = if (line.vertical) minOf(boxA.bottom, boxB.bottom) - maxOf(boxA.top, boxB.top)
            else minOf(boxA.right, boxB.right) - maxOf(boxA.left, boxB.left)
        val step = ((if (line.vertical) boxA.height else boxA.width) / first.size +
            (if (line.vertical) boxB.height else boxB.width) / second.size) / 2
        if (sharedLength <= 0f || abs(sharedLength - step * common) > step * 0.8f) return null
        val tail = b.word.text.offsetByCodePoints(0, common)
        val boxesA = a.word.characterBoxes?.takeIf { it.size == first.size }
        val boxesB = b.word.characterBoxes?.takeIf { it.size == second.size }
        return a.copy(word = a.word.copy(
            text = a.word.text + b.word.text.substring(tail), separator = b.word.separator,
            box = boxA.union(boxB),
            characterBoxes = if (boxesA != null && boxesB != null) boxesA + boxesB.drop(common) else null,
        ))
    }

    private fun duplicates(a: Word, b: Word, width: Int, height: Int): Boolean {
        val overlap = overlap(a.word.box, b.word.box)
        if (overlap < 0.55f) return false
        val first = Normalizer.normalize(a.word.text, Normalizer.Form.NFC)
        val second = Normalizer.normalize(b.word.text, Normalizer.Form.NFC)
        if (first == second) return true
        // A clipped token can be a prefix/suffix (or a differently segmented part) of the complete token.
        if (edgeMargin(b, width, height) <= 3f && first.contains(second)) return true
        if (edgeMargin(a, width, height) <= 3f && second.contains(first)) return true
        val intersection = a.word.box.intersection(b.word.box) ?: return false
        val union = area(a.word.box) + area(b.word.box) - area(intersection)
        return union > 0f && area(intersection) / union >= 0.8f
    }

    private fun edgeMargin(candidate: Word, width: Int, height: Int): Float {
        val box = candidate.word.box
        val tile = candidate.observation.tile
        return minOf(
            if (tile.left > 0) box.left - tile.left else Float.MAX_VALUE,
            if (tile.top > 0) box.top - tile.top else Float.MAX_VALUE,
            if (tile.right < width) tile.right - box.right else Float.MAX_VALUE,
            if (tile.bottom < height) tile.bottom - box.bottom else Float.MAX_VALUE,
        )
    }

    private fun along(box: Box, line: OcrLine): Float = when {
        line.vertical -> box.top
        line.rightToLeft -> -box.right
        else -> box.left
    }

    private fun gap(a: Box, b: Box, line: OcrLine): Float = when {
        line.vertical -> b.top - a.bottom
        line.rightToLeft -> a.left - b.right
        else -> b.left - a.right
    }

    private fun overlap(a: Box, b: Box): Float {
        val intersection = a.intersection(b) ?: return 0f
        val smaller = minOf(area(a), area(b))
        return if (smaller > 0f) area(intersection) / smaller else 0f
    }
    private fun area(box: Box) = box.width * box.height

    private class Groups(size: Int) {
        private val parent = IntArray(size) { it }
        fun root(i: Int): Int {
            if (parent[i] != i) parent[i] = root(parent[i])
            return parent[i]
        }
        fun join(a: Int, b: Int) { parent[root(b)] = root(a) }
    }
}
