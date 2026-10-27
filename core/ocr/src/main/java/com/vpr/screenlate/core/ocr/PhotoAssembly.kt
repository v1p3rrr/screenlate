package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
import java.text.Normalizer
import kotlin.math.abs

/**
 * Joins tile-local observations in original-image coordinates. Collinear fragments join across tile overlaps;
 * paragraph membership follows their original paragraphs. Each physical word is kept once, also where the tiles
 * grouped or oriented its line differently; suppression requires geometric overlap, never text identity alone.
 * Words away from artificial crop edges win, then longer readings over the shorter ones they contain; a word cut by
 * a crop edge (or ending within a glyph of it along its line, where an engine dropped the cut glyph) and covered by
 * complete words of another tile is a misreading of them. Character boxes and provenance survive.
 *
 * The [PhotoTiles.overview] pass adds only text larger than the tiles' overlap that lies whole in no tile, in place of
 * the tiles' words lying at least half inside it; its smaller text is read better by the tiles.
 */
object PhotoAssembly {
    fun assemble(width: Int, height: Int, language: Language, tiles: List<PhotoTilePage>, overview: PhotoTilePage? = null): OcrPage {
        require(width > 0 && height > 0)
        val large = overview?.let { largeText(it, tiles.map(PhotoTilePage::tile)) }
        val pages = if (large == null) tiles else {
            val boxes = large.page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.box }
            tiles.map { it.copy(page = outside(it.page, boxes, it.tile)) } + large
        }
        var paragraphCount = 0
        var wordCount = 0
        val lines = buildList {
            pages.forEachIndexed { tileIndex, result ->
                require(result.tile.right <= width && result.tile.bottom <= height)
                val placed = result.page.offset(result.tile.left.toFloat(), result.tile.top.toFloat())
                placed.paragraphs.forEach { paragraph ->
                    val paragraphId = paragraphCount++
                    paragraph.lines.filter { it.words.isNotEmpty() }.forEach { line ->
                        add(Observation(line.copy(words = line.words.map { word ->
                            word.copy(engine = word.engine ?: paragraph.engine ?: result.page.engine)
                        }), result.tile, tileIndex, paragraphId, wordCount))
                        wordCount += line.words.size
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
        val words = lines.flatMap { observation -> observation.line.words.map { Word(it, observation) } }
        val owner = owners(words, width, height)
        val merged = lines.indices.groupBy(lineGroups::root).values.mapNotNull { members ->
            val observations = members.map(lines::get)
            mergeLine(observations, words, owner, width, height, language)?.let { paragraphs.root(observations.first().paragraph) to it }
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
        val engine = pages.firstOrNull { it.page.engine == OcrEngineType.LENS }?.page?.engine
            ?: pages.firstOrNull()?.page?.engine ?: OcrEngineType.LENS
        return OcrPage(width, height, assembled, engine).inLanguage(language)
    }

    /**
     * The words of [overview] that the [tiles] cannot read whole: larger across their line than the tiles' overlap and
     * inside no tile. Null when there are none.
     */
    private fun largeText(overview: PhotoTilePage, tiles: List<PhotoTile>): PhotoTilePage? {
        val origin = overview.tile
        val paragraphs = overview.page.paragraphs.mapNotNull { paragraph ->
            val lines = paragraph.lines.mapNotNull { line ->
                val words = line.words.filter { word ->
                    val size = if (line.vertical) word.box.width else word.box.height
                    val placed = word.box.offset(origin.left.toFloat(), origin.top.toFloat())
                    size > PhotoTiles.OVERLAP && tiles.none { inside(placed, it) }
                }
                Box.unionOf(words.map { it.box })?.let { box -> line.copy(words = words, box = box) }
            }
            if (lines.isEmpty()) null else paragraph.copy(lines = lines)
        }
        return if (paragraphs.isEmpty()) null else overview.copy(page = overview.page.copy(paragraphs = paragraphs))
    }

    /** [page] of [tile] without the words lying at least half inside [boxes], given in photo pixels. */
    private fun outside(page: OcrPage, boxes: List<Box>, tile: PhotoTile): OcrPage = page.copy(
        paragraphs = page.paragraphs.map { paragraph ->
            paragraph.copy(
                lines = paragraph.lines.map { line ->
                    val words = line.words.filter { word ->
                        val placed = word.box.offset(tile.left.toFloat(), tile.top.toFloat())
                        val covered = boxes.sumOf { box -> (placed.intersection(box)?.let(::area) ?: 0f).toDouble() }
                        area(placed) <= 0f || covered < area(placed) * 0.5
                    }
                    line.copy(words = words, box = Box.unionOf(words.map { it.box }) ?: line.box)
                },
            )
        },
    )

    private fun inside(box: Box, tile: PhotoTile) =
        box.left >= tile.left && box.top >= tile.top && box.right <= tile.right && box.bottom <= tile.bottom

    /** [firstWord] is the index of the line's first word among all words of the photo. */
    private data class Observation(val line: OcrLine, val tile: PhotoTile, val tileIndex: Int, val paragraph: Int, val firstWord: Int)
    private data class Word(val word: OcrWord, val observation: Observation)

    private fun sameLine(a: Observation, b: Observation): Boolean {
        if (a.tileIndex == b.tileIndex || !a.tile.box.intersects(b.tile.box)) return false
        val x = a.line
        val y = b.line
        // Directions may differ: an engine takes a part's direction from its own text, and a tile may see only a run of
        // the other direction or numbers (a Latin name at the end of an Arabic line).
        if (x.vertical != y.vertical) return false
        // A shared physical word anchors the merge even if the engines grouped the rest of the line differently, or the
        // line boxes are offset across the line, as those of a slanted line's parts in two tiles are.
        if (x.words.any { first -> y.words.any { second -> sameWord(first.box, second.box, x.vertical) } }) return true
        val sizeA = if (x.vertical) x.box.width else x.box.height
        val sizeB = if (y.vertical) y.box.width else y.box.height
        if (minOf(sizeA, sizeB) <= 0f || minOf(sizeA, sizeB) < maxOf(sizeA, sizeB) * 0.5f) return false
        val crossOverlap = if (x.vertical) minOf(x.box.right, y.box.right) - maxOf(x.box.left, y.box.left)
            else minOf(x.box.bottom, y.box.bottom) - maxOf(x.box.top, y.box.top)
        val centerGap = if (x.vertical) abs(x.box.centerX - y.box.centerX) else abs(x.box.centerY - y.box.centerY)
        if (crossOverlap < minOf(sizeA, sizeB) * 0.65f || centerGap > maxOf(sizeA, sizeB) * 0.4f) return false
        val alongOverlap = if (x.vertical) minOf(x.box.bottom, y.box.bottom) - maxOf(x.box.top, y.box.top)
            else minOf(x.box.right, y.box.right) - maxOf(x.box.left, y.box.left)
        return alongOverlap > 0f
    }

    /** Boxes of one physical word: mostly overlapping, of a similar size and centered alike across the line. */
    private fun sameWord(a: Box, b: Box, vertical: Boolean): Boolean {
        if (overlap(a, b) < 0.5f) return false
        val sizeA = if (vertical) a.width else a.height
        val sizeB = if (vertical) b.width else b.height
        val gap = if (vertical) abs(a.centerX - b.centerX) else abs(a.centerY - b.centerY)
        return minOf(sizeA, sizeB) >= maxOf(sizeA, sizeB) * 0.65f && gap <= maxOf(sizeA, sizeB) * 0.4f
    }

    /**
     * Keeps one observation of each physical word across all tiles and lines. Returns, for each of [words], the index
     * of the word kept in its place; a kept word owns itself.
     */
    private fun owners(words: List<Word>, width: Int, height: Int): IntArray {
        val texts = Array(words.size) { glyphs(words[it].word.text) }
        val cut = BooleanArray(words.size) { cut(words[it], texts[it], width, height) }
        val margins = FloatArray(words.size) { edgeMargin(words[it], width, height) }
        val lengths = IntArray(words.size) { texts[it].size }
        val index = BoxIndex(words.map { it.word.box })
        val owner = IntArray(words.size) { -1 }
        // Complete words first, then the longer readings that contain shorter ones; among equal words, the one whose
        // line saw more of its context and lies farther from a crop edge.
        val priority = words.indices.sortedWith(compareByDescending<Int> { !cut[it] }
            .thenByDescending { lengths[it] }
            .thenByDescending { words[it].observation.line.words.size }
            .thenByDescending { margins[it] }
            .thenByDescending { words[it].word.characterBoxes != null })
        for (i in priority) {
            if (owner[i] >= 0) continue
            val box = words[i].word.box
            val nearby = index.query(box).filter { words[it].observation.tileIndex != words[i].observation.tileIndex }
            if (cut[i]) {
                // Complete words of another tile over most of a cut word read it with its context: the cut one,
                // whatever its text, is a misreading of them.
                val cover = nearby.filter { owner[it] == it && !cut[it] }
                    .associateWith { box.intersection(words[it].word.box)?.let(::area) ?: 0f }
                if (cover.isNotEmpty() && cover.values.sum() >= area(box) * 0.5f) {
                    owner[i] = cover.maxBy { it.value }.key
                    continue
                }
            }
            owner[i] = i
            for (j in nearby) if (owner[j] < 0 && duplicates(words[i], words[j])) owner[j] = i
        }
        return owner
    }

    private fun mergeLine(
        observations: List<Observation>,
        words: List<Word>,
        owner: IntArray,
        width: Int,
        height: Int,
        language: Language,
    ): OcrLine? {
        // Parts of differing direction hold a run of the other direction: the line takes the language's.
        val example = observations.first().line.let { first ->
            if (observations.all { it.line.rightToLeft == first.rightToLeft }) first
            else first.copy(rightToLeft = language.support.rightToLeft)
        }
        val ranges = observations.map { it.firstWord until it.firstWord + it.line.words.size }
        val chosen = ranges.flatten().filter { owner[it] == it }
        if (chosen.isEmpty()) return null
        // Keep each engine's logical word order (including Latin runs inside RTL lines). Geometry only orders
        // fragments whose original observations give no ordering constraint. A word kept in another line (a tile
        // grouped or oriented it differently) orders nothing here.
        val edges = chosen.associateWith { mutableSetOf<Int>() }
        for (range in ranges) {
            val members = range.map { owner[it] }.filter { it in edges }.distinct()
            members.zipWithNext().forEach { (from, to) -> edges.getValue(from).add(to) }
        }
        val pending = chosen.toMutableSet()
        val sorted = buildList {
            while (pending.isNotEmpty()) {
                val free = pending.filter { target -> pending.none { target in edges.getValue(it) } }
                val next = (free.ifEmpty { pending.toList() }).minBy { along(words[it].word.box, example) }
                add(words[next])
                pending.remove(next)
            }
        }
        val stitched = mutableListOf<Word>()
        for (candidate in sorted) {
            val previous = stitched.lastOrNull()
            val joined = previous?.let { stitch(it, candidate, example, width, height) }
            if (joined != null) stitched[stitched.lastIndex] = joined else stitched += candidate
        }
        val result = stitched.mapIndexed { i, candidate ->
            val word = candidate.word
            // At an artificial tile edge an engine may omit a word separator. Use the language's rule only for
            // physically separated words; never change an explicit separator or split a combining sequence.
            val next = stitched.getOrNull(i + 1)?.word
            val gap = next?.let { gap(word.box, it.box, example) } ?: 0f
            val size = if (example.vertical) word.box.width else word.box.height
            if (word.separator.isEmpty() && gap > size * 0.08f) word.copy(separator = language.support.wordSeparator) else word
        }
        return example.copy(words = result, box = Box.unionOf(result.map { it.box }) ?: example.box)
    }

    /**
     * Very long words can be clipped in both tiles; join only a geometrically consistent shared suffix/prefix. The
     * pieces follow the word's own direction, not the line's: a Latin word or a number inside a right-to-left line
     * starts at its left end. The joined word takes [a]'s place in [line].
     */
    private fun stitch(a: Word, b: Word, line: OcrLine, width: Int, height: Int): Word? {
        if (a.observation.tileIndex == b.observation.tileIndex) return null
        val textA = Normalizer.normalize(a.word.text, Normalizer.Form.NFC)
        val textB = Normalizer.normalize(b.word.text, Normalizer.Form.NFC)
        if (textA.isEmpty() || textB.isEmpty()) return null
        val rightToLeft = !line.vertical && isRightToLeftWord(textA + textB, line.rightToLeft)
        val aStarts = when {
            line.vertical -> a.word.box.top <= b.word.box.top
            rightToLeft -> a.word.box.right >= b.word.box.right
            else -> a.word.box.left <= b.word.box.left
        }
        // The piece with the word's start, then the one with its end.
        val (head, tail) = if (aStarts) a to b else b to a
        val (headText, tailText) = if (aStarts) textA to textB else textB to textA
        val headTile = head.observation.tile
        val tailTile = tail.observation.tile
        val boxA = head.word.box
        val boxB = tail.word.box
        val first = headText.codePoints().toArray()
        val second = tailText.codePoints().toArray()
        // A glyph's length: marks share the place of their letter.
        val step = ((if (line.vertical) boxA.height else boxA.width) / places(first) +
            (if (line.vertical) boxB.height else boxB.width) / places(second)) / 2
        // An engine may drop the glyph cut by the edge, leaving the box up to a glyph short of it.
        val band = maxOf(CLIPPED, step)
        val endClipped = when {
            line.vertical -> headTile.bottom < height && abs(boxA.bottom - headTile.bottom) <= band
            rightToLeft -> headTile.left > 0 && abs(boxA.left - headTile.left) <= band
            else -> headTile.right < width && abs(boxA.right - headTile.right) <= band
        }
        val startClipped = when {
            line.vertical -> tailTile.top > 0 && abs(boxB.top - tailTile.top) <= band
            rightToLeft -> tailTile.right < width && abs(boxB.right - tailTile.right) <= band
            else -> tailTile.left > 0 && abs(boxB.left - tailTile.left) <= band
        }
        if (!endClipped || !startClipped) return null
        val sharedLength = if (line.vertical) minOf(boxA.bottom, boxB.bottom) - maxOf(boxA.top, boxB.top)
            else minOf(boxA.right, boxB.right) - maxOf(boxA.left, boxB.left)
        if (sharedLength <= 0f) return null
        // A box that reaches the edge also spans the dropped part of the cut glyph: up to a glyph more at each end.
        val common = (minOf(first.size, second.size) downTo 1).firstOrNull { count ->
            first.takeLast(count) == second.take(count) &&
                sharedLength - step * places(first, first.size - count) in -0.8f * step..2f * step
        } ?: return null
        val rest = tailText.offsetByCodePoints(0, common)
        val boxesA = head.word.characterBoxes?.takeIf { it.size == first.size }
        val boxesB = tail.word.characterBoxes?.takeIf { it.size == second.size }
        return a.copy(word = a.word.copy(
            text = headText + tailText.substring(rest), separator = b.word.separator,
            box = boxA.union(boxB),
            characterBoxes = if (boxesA != null && boxesB != null) boxesA + boxesB.drop(common) else null,
        ))
    }

    /** Whether [b] reads the same physical text as [a], which was kept before it. */
    private fun duplicates(a: Word, b: Word): Boolean {
        val overlap = overlap(a.word.box, b.word.box)
        if (overlap < 0.55f) return false
        val first = Normalizer.normalize(a.word.text, Normalizer.Form.NFC)
        val second = Normalizer.normalize(b.word.text, Normalizer.Form.NFC)
        // A clipped or differently segmented token can be a part of the longer one.
        if (first.contains(second)) return true
        val intersection = a.word.box.intersection(b.word.box) ?: return false
        val union = area(a.word.box) + area(b.word.box) - area(intersection)
        return union > 0f && area(intersection) / union >= 0.8f
    }

    /**
     * Whether a crop edge inside the photo may cut [candidate]. Along the word's line an engine may drop the glyph the
     * edge cuts, leaving the box up to a glyph short of the edge; across the line the box reaches the edge. [glyphs]
     * are the word's code points; marks share the place of their letter.
     */
    private fun cut(candidate: Word, glyphs: IntArray, width: Int, height: Int): Boolean {
        val box = candidate.word.box
        val tile = candidate.observation.tile
        val vertical = candidate.observation.line.vertical
        val along = maxOf(CLIPPED, (if (vertical) box.height else box.width) / maxOf(1, places(glyphs)))
        val sides = if (vertical) CLIPPED else along
        val ends = if (vertical) along else CLIPPED
        return tile.left > 0 && box.left - tile.left <= sides || tile.right < width && tile.right - box.right <= sides ||
            tile.top > 0 && box.top - tile.top <= ends || tile.bottom < height && tile.bottom - box.bottom <= ends
    }

    private fun glyphs(text: String): IntArray = Normalizer.normalize(text, Normalizer.Form.NFC).codePoints().toArray()

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

    /** A word's box within this distance of a crop edge inside the photo may be cut by it; along its line, a glyph. */
    private const val CLIPPED = 3f

    private class Groups(size: Int) {
        private val parent = IntArray(size) { it }
        fun root(i: Int): Int {
            if (parent[i] != i) parent[i] = root(parent[i])
            return parent[i]
        }
        fun join(a: Int, b: Int) { parent[root(b)] = root(a) }
    }
}
