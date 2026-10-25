package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Matrix
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoViewport

/**
 * The power of two to divide the stored image by for drawing it [scale] times its size: the largest that still leaves
 * at least the drawn number of pixels, so the photo stays as sharp as the screen allows.
 */
internal fun displaySampleSize(scale: Float): Int {
    var size = 1
    while (size * 2 * scale <= 1f) size *= 2
    return size
}

/** The stored image reduced by [sampleSize], as stored (not turned upright); null when it cannot be decoded. */
internal fun decodeForDisplay(document: PhotoDocument, sampleSize: Int): Bitmap? =
    BitmapFactory.decodeFile(document.image.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })

/**
 * Maps a pixel of the stored image to the viewer: turned upright as its orientation says, then placed where
 * [viewport] puts the photo on the screen, relative to the viewer's top left corner ([originX], [originY]) there.
 */
internal fun imageToViewer(document: PhotoDocument, viewport: PhotoViewport, originX: Float, originY: Float): Matrix {
    val orientation = document.orientation
    val w = document.rawWidth
    val h = document.rawHeight
    // The orientation is a flip or a quarter turn, so three points fix it.
    val (x0, y0) = orientation.toOriented(0f, 0f, w, h)
    val (x1, y1) = orientation.toOriented(1f, 0f, w, h)
    val (x2, y2) = orientation.toOriented(0f, 1f, w, h)
    val scale = viewport.scale
    // Column-major: x' = v0·x + v4·y + v12, y' = v1·x + v5·y + v13.
    return Matrix(
        floatArrayOf(
            (x1 - x0) * scale, (y1 - y0) * scale, 0f, 0f,
            (x2 - x0) * scale, (y2 - y0) * scale, 0f, 0f,
            0f, 0f, 1f, 0f,
            x0 * scale + viewport.offsetX - originX, y0 * scale + viewport.offsetY - originY, 0f, 1f,
        ),
    )
}
