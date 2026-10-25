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
