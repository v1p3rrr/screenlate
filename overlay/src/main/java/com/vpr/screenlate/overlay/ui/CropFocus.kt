package com.vpr.screenlate.overlay.ui

import com.vpr.screenlate.core.common.geometry.Box

/** The crop editor's first frame. */
object CropFocus {
    /**
     * The looked-up word's paragraph ([lines]) plus [padding] on every side, clipped to [image]. Lines without a
     * size are left out, as they carry no position; null when nothing is left or the frame misses the image.
     */
    fun of(lines: List<Box>, padding: Float, image: Box): Box? {
        val text = Box.unionOf(lines.filter { it.width > 0f && it.height > 0f }) ?: return null
        val frame = Box(
            maxOf(text.left - padding, image.left),
            maxOf(text.top - padding, image.top),
            minOf(text.right + padding, image.right),
            minOf(text.bottom + padding, image.bottom),
        )
        return frame.takeIf { it.width > 0f && it.height > 0f }
    }
}
