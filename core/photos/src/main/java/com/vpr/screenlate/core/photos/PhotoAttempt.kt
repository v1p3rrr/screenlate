package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile

/** Durable progress, separate from the committed lookup text. A restored unfinished attempt never starts itself. */
data class PhotoAttempt(
    val id: String,
    val basedOn: Int,
    val language: Language,
    val settings: PhotoOcrSettings,
    val tiles: List<PhotoTile>,
    val results: List<PhotoTileResult> = emptyList(),
    val startedAt: Long,
    val finished: Boolean = false,
) {
    init {
        require(id.isNotBlank() && id.length <= 64 && basedOn >= 0)
        require(tiles.isNotEmpty() && tiles.distinct().size == tiles.size)
        require(results.map { it.tile }.distinct().size == results.size && results.all { it.tile in tiles })
        require(!finished || results.size == tiles.size)
    }

    val remaining: List<PhotoTile> get() = tiles.filter { tile -> results.none { it.tile == tile && it.page != null } }
}

/** Exactly one terminal tile outcome, still in the tile's pixel coordinates. */
data class PhotoTileResult(val tile: PhotoTile, val page: OcrPage? = null, val failure: FailureCause? = null) {
    init { require((page == null) != (failure == null)) }
}
