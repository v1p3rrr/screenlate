package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.PhotoTile
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Owned original pixels, independent of the current document. Close after all reads finish. Returned bitmaps belong
 * to the caller; preview sampling never changes crop coordinates. The store keeps the image file, even of a photo
 * deleted meanwhile, until the first close runs [release].
 */
class PhotoImage internal constructor(private val document: PhotoDocument, private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val width: Int get() = document.width
    val height: Int get() = document.height
    val bounds: Box get() = Box(0f, 0f, width.toFloat(), height.toFloat())

    suspend fun preview(maxWidth: Int, maxHeight: Int): Bitmap {
        require(maxWidth > 0 && maxHeight > 0)
        var sample = 1
        while (width / sample > maxWidth || height / sample > maxHeight) sample *= 2
        return read(PhotoTile(0, 0, width, height), sample)
    }

    /**
     * Rounds outwards in oriented original pixels so a fractional selection never cuts off a boundary pixel. A crop of
     * more than [maxPixels] pixels is scaled down in proportion to that many.
     */
    suspend fun crop(selection: Box, maxPixels: Long = Long.MAX_VALUE): Bitmap {
        require(listOf(selection.left, selection.top, selection.right, selection.bottom).all { it.isFinite() })
        val box = requireNotNull(selection.intersection(bounds)) { "Empty photo crop" }
        val tile = PhotoTile(floor(box.left).toInt(), floor(box.top).toInt(), ceil(box.right).toInt(), ceil(box.bottom).toInt())
        val size = fittedSize(tile.width, tile.height, maxPixels)
        return read(tile, 1, size.takeIf { it != tile.width to tile.height })
    }

    private suspend fun read(tile: PhotoTile, sample: Int, size: Pair<Int, Int>? = null): Bitmap {
        var result: Bitmap? = null
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                PhotoRegions(document).use { result = it.read(tile, sample, size) }
            }
            currentCoroutineContext().ensureActive()
            return result!!
        } catch (e: Exception) { result?.recycle(); throw e }
    }

    /** Copies ordinary original image bytes, including their orientation; no OCR data is written. */
    suspend fun copyTo(output: OutputStream) = withContext(Dispatchers.IO) {
        document.image.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
    }

    internal fun mimeType(): String = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(document.image.path, this)
    }.outMimeType ?: "image/jpeg"

    override fun close() { if (closed.compareAndSet(false, true)) release() }
}
