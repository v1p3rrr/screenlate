package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.Matrix
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoViewport
import kotlin.math.ceil
import kotlin.math.floor

internal data class PhotoDetail(val bitmap: Bitmap, val raw: Rect)

/** The raw region visible through the current viewport; display quality never requires a full-resolution whole image. */
internal fun visibleRawRegion(document: PhotoDocument, viewport: PhotoViewport): Rect? {
    val shown = viewport.visible ?: return null
    val photo = com.vpr.screenlate.core.common.geometry.Box(
        (shown.left - viewport.offsetX) / viewport.scale, (shown.top - viewport.offsetY) / viewport.scale,
        (shown.right - viewport.offsetX) / viewport.scale, (shown.bottom - viewport.offsetY) / viewport.scale,
    )
    val raw = document.orientation.toRaw(photo, document.rawWidth, document.rawHeight)
    return Rect(floor(raw.left).toInt().coerceIn(0, document.rawWidth), floor(raw.top).toInt().coerceIn(0, document.rawHeight),
        ceil(raw.right).toInt().coerceIn(0, document.rawWidth), ceil(raw.bottom).toInt().coerceIn(0, document.rawHeight))
}

/** A sharper display patch only for the visible region, decoded independently of OCR. */
@Suppress("DEPRECATION") // minSdk 30 supports this overload.
internal fun decodeDetail(document: PhotoDocument, raw: Rect, sample: Int): PhotoDetail? {
    val decoder = BitmapRegionDecoder.newInstance(document.image.path, false)
    return try {
        decoder.decodeRegion(raw, BitmapFactory.Options().apply { inSampleSize = sample })?.let { PhotoDetail(it, raw) }
    } finally { decoder.recycle() }
}

/**
 * The power of two to divide the stored image by for drawing it [scale] times its size: the largest that still leaves
 * at least the drawn number of pixels, so the photo stays as sharp as the screen allows.
 */
internal fun displaySampleSize(scale: Float): Int {
    require(scale.isFinite() && scale > 0f) { "Invalid display scale" }
    var size = 1
    while (size <= Int.MAX_VALUE / 2 && size * 2L * scale <= 1f) size *= 2
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
