package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import kotlin.random.Random
import org.junit.Test

class PhotoTilesTest {
    @Test
    fun everyAxisAndFarEdgeIsCoveredAtOriginalResolution() {
        val random = Random(817)
        for ((width, height) in listOf(1 to 1, 1500 to 1500, 1501 to 1501, 4000 to 3000) +
            List(100) { random.nextInt(1, 15000) to random.nextInt(1, 15000) }) {
            val tiles = PhotoTiles.grid(width, height)
            assertThat(tiles.distinct()).hasSize(tiles.size)
            assertThat(tiles.all { it.width in 1..1500 && it.height in 1..1500 }).isTrue()
            val columns = tiles.map { it.left to it.right }.distinct().sortedBy { it.first }
            val rows = tiles.map { it.top to it.bottom }.distinct().sortedBy { it.first }
            for ((axis, length) in listOf(columns to width, rows to height)) {
                assertThat(axis.first().first).isEqualTo(0)
                assertThat(axis.last().second).isEqualTo(length)
                axis.zipWithNext().forEach { (a, b) -> assertThat(a.second - b.first).isAtLeast(PhotoTiles.OVERLAP) }
            }
            assertThat(tiles).hasSize(columns.size * rows.size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyImagesAreRejected() { PhotoTiles.grid(0, 3000) }

    @Test
    fun onlyAPhotoOfSeveralTilesHasAnOverview() {
        assertThat(PhotoTiles.overview(1500, 1200)).isNull()
        assertThat(PhotoTiles.overview(1501, 1200)).isEqualTo(PhotoTile(0, 0, 1501, 1200))
        assertThat(PhotoTiles.isOverview(PhotoTile(0, 0, 4000, 3000), 4000, 3000)).isTrue()
        assertThat(PhotoTiles.isOverview(PhotoTile(0, 0, 1500, 1500), 1500, 1500)).isFalse()
        assertThat(PhotoTiles.grid(4000, 3000).none { PhotoTiles.isOverview(it, 4000, 3000) }).isTrue()
    }

    @Test
    fun aScaledPageKeepsEveryBoxInPlace() {
        val word = OcrWord("word", " ", Box(10f, 20f, 50f, 40f), characterBoxes = listOf(Box(10f, 20f, 30f, 40f), Box(30f, 20f, 50f, 40f)))
        val page = OcrPage(100, 50, listOf(OcrParagraph(listOf(OcrLine(listOf(word), word.box, false)))), OcrEngineType.LENS)
        val scaled = page.scaled(300, 200)
        assertThat(scaled.width to scaled.height).isEqualTo(300 to 200)
        val line = scaled.paragraphs.single().lines.single()
        assertThat(line.box).isEqualTo(Box(30f, 80f, 150f, 160f))
        assertThat(line.words.single().box).isEqualTo(Box(30f, 80f, 150f, 160f))
        assertThat(line.words.single().characterBoxes).containsExactly(Box(30f, 80f, 90f, 160f), Box(90f, 80f, 150f, 160f)).inOrder()
    }
}
