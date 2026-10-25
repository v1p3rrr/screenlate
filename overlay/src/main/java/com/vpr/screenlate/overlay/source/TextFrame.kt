package com.vpr.screenlate.overlay.source

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.core.photos.PhotoViewport

/**
 * Where the open scan's text layout lies on the screen. A screen scan's layout is in screen pixels; a photo's stays
 * in the photo's pixels and is placed by its viewport, which moves without changing the layout.
 */
internal sealed interface TextFrame {
    /** The layout point under the screen point ([x], [y]); null where there is no text to aim at. */
    fun toLayout(x: Float, y: Float): Pair<Float, Float>?

    /** A distance on the screen, such as a touch tolerance, in layout pixels. */
    fun toLayoutDistance(screen: Float): Float

    /** The layout's [box] on the screen, cut to the part that shows; null when none of it does. */
    fun toScreen(box: Box): Box?

    data object Screen : TextFrame {
        override fun toLayout(x: Float, y: Float) = x to y

        override fun toLayoutDistance(screen: Float) = screen

        override fun toScreen(box: Box) = box
    }

    /** Toolbars, navigation and the letterbox around the photo are no part of it. */
    data class Photo(val viewport: PhotoViewport) : TextFrame {
        override fun toLayout(x: Float, y: Float) = viewport.toPhoto(x, y)

        override fun toLayoutDistance(screen: Float) = viewport.toPhotoDistance(screen)

        override fun toScreen(box: Box) = viewport.toVisibleScreen(box)
    }
}

/** [TextLayout.hitTest] at the screen point ([x], [y]), with a [tolerance] in screen pixels. */
internal fun TextLayout.hitTest(frame: TextFrame, x: Float, y: Float, tolerance: Float): TextPosition? {
    val (layoutX, layoutY) = frame.toLayout(x, y) ?: return null
    return hitTest(layoutX, layoutY, frame.toLayoutDistance(tolerance))
}
