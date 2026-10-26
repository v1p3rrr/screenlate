package com.vpr.screenlate.overlay.ui

import com.vpr.screenlate.core.common.geometry.Box

/** One invertible placement for drawing a preview, selecting its frame and cropping the original pixels. */
internal data class CropPlacement(val source: Box, val scale: Float, val left: Float, val top: Float) {
    val shown: Box get() = toScreen(source)
    fun toScreen(box: Box) = Box(left + (box.left - source.left) * scale, top + (box.top - source.top) * scale,
        left + (box.right - source.left) * scale, top + (box.bottom - source.top) * scale)
    fun toSource(box: Box) = Box(source.left + (box.left - left) / scale, source.top + (box.top - top) / scale,
        source.left + (box.right - left) / scale, source.top + (box.bottom - top) / scale)

    companion object {
        fun of(source: Box, area: Box, fit: Boolean): CropPlacement {
            require(source.width > 0 && source.height > 0 && area.width > 0 && area.height > 0)
            if (!fit) return CropPlacement(source, 1f, source.left, source.top)
            val scale = minOf(area.width / source.width, area.height / source.height)
            return CropPlacement(source, scale, area.left + (area.width - source.width * scale) / 2,
                area.top + (area.height - source.height * scale) / 2)
        }
    }
}
