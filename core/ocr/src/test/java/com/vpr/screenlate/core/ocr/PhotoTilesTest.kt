package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
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
}
