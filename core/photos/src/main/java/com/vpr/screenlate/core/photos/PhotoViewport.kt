package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.geometry.Box

/**
 * Where a photo is drawn on the screen: a point of the photo (oriented pixels) is at `point × scale + offset` on the
 * screen, in the screen pixels the overlay's windows use. Only [visible] shows the photo; bars, controls and the
 * letterbox around it are not part of it, and neither is the photo outside the viewer's area.
 *
 * @property area the viewer's part of the screen.
 */
data class PhotoViewport(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val photoWidth: Int,
    val photoHeight: Int,
    val area: Box,
) {
    init {
        require(scale > 0f && photoWidth > 0 && photoHeight > 0) { "Empty viewport" }
    }

    /** The photo's part of the screen; null when none of it is in [area]. */
    val visible: Box? = toScreen(Box(0f, 0f, photoWidth.toFloat(), photoHeight.toFloat())).intersection(area)

    /** The photo's point under the screen point ([x], [y]); null when the photo does not show there. */
    fun toPhoto(x: Float, y: Float): Pair<Float, Float>? {
        if (visible?.contains(x, y) != true) return null
        return (x - offsetX) / scale to (y - offsetY) / scale
    }

    /** [box] of the photo on the screen, whether or not it shows there. */
    fun toScreen(box: Box): Box = Box(
        box.left * scale + offsetX,
        box.top * scale + offsetY,
        box.right * scale + offsetX,
        box.bottom * scale + offsetY,
    )

    /** The part of [box] of the photo that shows on the screen; null when none does. */
    fun toVisibleScreen(box: Box): Box? {
        val shown = visible ?: return null
        return toScreen(box).intersection(shown)
    }

    /** A distance on the screen, such as a touch tolerance, in photo pixels. */
    fun toPhotoDistance(screen: Float): Float = screen / scale

    /** Keeps the focal photo point under [focusX]/[focusY], then pans and constrains the image to its visible edges. */
    fun transformed(focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float): PhotoViewport {
        require(zoom.isFinite() && zoom > 0f && listOf(focusX, focusY, panX, panY).all { it.isFinite() })
        val fit = fit(photoWidth, photoHeight, area).scale
        val next = (scale * zoom).coerceIn(fit, fit * 8f)
        val ratio = next / scale
        fun constrain(offset: Float, start: Float, end: Float, size: Float): Float =
            if (size <= end - start) start + (end - start - size) / 2 else offset.coerceIn(end - size, start)
        return copy(
            scale = next,
            offsetX = constrain(focusX + (offsetX - focusX) * ratio + panX, area.left, area.right, photoWidth * next),
            offsetY = constrain(focusY + (offsetY - focusY) * ratio + panY, area.top, area.bottom, photoHeight * next),
        )
    }

    /**
     * The same view in a resized [area], as when the controls above the viewer change height: the zoom relative to the
     * fitted view is kept and the photo point at the old area's center moves to the new center, within the edges.
     */
    fun resized(area: Box): PhotoViewport {
        if (area == this.area) return this
        val zoom = scale / fit(photoWidth, photoHeight, this.area).scale
        val centerX = (this.area.centerX - offsetX) / scale
        val centerY = (this.area.centerY - offsetY) / scale
        val fitted = fit(photoWidth, photoHeight, area)
        val next = fitted.scale * zoom
        return fitted.copy(scale = next, offsetX = area.centerX - centerX * next, offsetY = area.centerY - centerY * next)
            .transformed(area.centerX, area.centerY, 0f, 0f, 1f)
    }

    companion object {
        /** The whole photo as large as it fits in [area], centered: the view a photo opens with. */
        fun fit(photoWidth: Int, photoHeight: Int, area: Box): PhotoViewport {
            val scale = minOf(area.width / photoWidth, area.height / photoHeight)
            return PhotoViewport(
                scale = scale,
                offsetX = area.left + (area.width - photoWidth * scale) / 2f,
                offsetY = area.top + (area.height - photoHeight * scale) / 2f,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                area = area,
            )
        }
    }
}
