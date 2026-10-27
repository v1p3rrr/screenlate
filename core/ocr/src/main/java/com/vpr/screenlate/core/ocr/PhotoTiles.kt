package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

/** A region decoded at original resolution. Right and bottom are exclusive pixel edges. */
data class PhotoTile(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init { require(left >= 0 && top >= 0 && right > left && bottom > top) }
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val box: Box get() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

/** Full two-axis coverage, including partial-sized images and both far edges; independent of screen scan settings. */
object PhotoTiles {
    const val MAX_SIDE = 1500
    const val OVERLAP = 200

    /**
     * The whole photo, read once more scaled down to [MAX_SIDE], for text too large to lie whole in any tile of the
     * [grid]; null when the grid has a single tile. It is not one of the photo's regions.
     */
    fun overview(width: Int, height: Int): PhotoTile? =
        PhotoTile(0, 0, width, height).takeIf { width > MAX_SIDE || height > MAX_SIDE }

    fun isOverview(tile: PhotoTile, width: Int, height: Int): Boolean = tile == overview(width, height)

    fun grid(width: Int, height: Int): List<PhotoTile> {
        require(width > 0 && height > 0)
        return starts(height).flatMap { y ->
            starts(width).map { x -> PhotoTile(x, y, minOf(x.toLong() + MAX_SIDE, width.toLong()).toInt(),
                minOf(y.toLong() + MAX_SIDE, height.toLong()).toInt()) }
        }
    }

    private fun starts(size: Int): List<Int> {
        if (size <= MAX_SIDE) return listOf(0)
        val last = size - MAX_SIDE
        return buildList {
            var start = 0
            add(start)
            while (start < last) {
                start = minOf(start.toLong() + MAX_SIDE - OVERLAP, last.toLong()).toInt()
                add(start)
            }
        }
    }
}

/** A tile's page still in tile-local coordinates; assembly maps it to original-photo pixels. */
data class PhotoTilePage(val tile: PhotoTile, val page: OcrPage)
