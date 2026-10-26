package com.vpr.screenlate.overlay.anki

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.photos.PhotoImage
import com.vpr.screenlate.overlay.capture.SharedScreenshot

/** One note's owned image. Coordinates are source pixels; previews are borrowed until close, crops are caller-owned. */
interface NoteImage : AutoCloseable {
    val bounds: Box
    val fit: Boolean
    suspend fun preview(maxWidth: Int, maxHeight: Int): Bitmap
    suspend fun crop(selection: Box): Bitmap
}

/** Retains the existing screenshot ownership and natural screen placement. Takes an already retained reference. */
class ScreenNoteImage(private val shot: SharedScreenshot) : NoteImage {
    override val bounds = Box(shot.screen.left.toFloat(), shot.screen.top.toFloat(),
        (shot.screen.left + shot.bitmap.width).toFloat(), (shot.screen.top + shot.bitmap.height).toFloat())
    override val fit = false
    private var closed = false
    override suspend fun preview(maxWidth: Int, maxHeight: Int) = shot.bitmap
    override suspend fun crop(selection: Box): Bitmap {
        val image = shot.bitmap
        val box = requireNotNull(selection.intersection(bounds))
        val left = (box.left - bounds.left).toInt().coerceIn(0, image.width - 1)
        val top = (box.top - bounds.top).toInt().coerceIn(0, image.height - 1)
        val crop = Bitmap.createBitmap(image, left, top, box.width.toInt().coerceIn(1, image.width - left),
            box.height.toInt().coerceIn(1, image.height - top))
        return if (crop === image) image.copy(image.config ?: Bitmap.Config.ARGB_8888, false) else crop
    }
    override fun close() { if (!closed) { closed = true; shot.release() } }
}

/** A sampled upright preview and an original-pixel crop of the pinned photograph. */
class PhotoNoteImage(private val image: PhotoImage) : NoteImage {
    override val bounds = image.bounds
    override val fit = true
    private var preview: Bitmap? = null
    override suspend fun preview(maxWidth: Int, maxHeight: Int): Bitmap =
        preview ?: image.preview(maxWidth, maxHeight).also { preview = it }
    override suspend fun crop(selection: Box) = image.crop(selection)
    override fun close() { preview?.recycle(); preview = null; image.close() }
}
