package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.vpr.screenlate.core.ocr.PhotoTile
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Makes the history's small copy of a photo. */
internal fun interface ThumbnailMaker {
    suspend fun thumbnail(document: PhotoDocument): ByteArray
}

/** An upright JPEG whose shorter side is [SIDE] pixels, enough for a grid cell or the button next to the shutter. */
internal object PhotoThumbnails : ThumbnailMaker {
    const val SIDE = 320

    override suspend fun thumbnail(document: PhotoDocument): ByteArray {
        var sample = 1
        while (minOf(document.width, document.height) / (sample * 2) >= SIDE) sample *= 2
        val bitmap = PhotoRegions(document).use { it.read(PhotoTile(0, 0, document.width, document.height), sample) }
        try {
            val scale = SIDE.toFloat() / minOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
            } else {
                bitmap
            }
            try {
                return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private const val QUALITY = 85
}
