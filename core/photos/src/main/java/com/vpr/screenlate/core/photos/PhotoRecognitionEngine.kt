package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTiles
import com.vpr.screenlate.core.ocr.model.DeviceOcr
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Opens fixed settings and image ownership for one multi-tile attempt. The device model of [settings] is used as
 * named, never another; a retry passes the settings of the attempt it continues.
 */
interface PhotoRecognitionEngine {
    suspend fun open(document: PhotoDocument, language: Language, settings: PhotoOcrSettings): PhotoOcrSession
}

interface PhotoOcrSession : AutoCloseable {
    val settings: PhotoOcrSettings

    /** Reads [tile], scaled down to [PhotoTiles.MAX_SIDE] when larger; the page is in the tile's own pixels. */
    suspend fun recognize(tile: PhotoTile): OcrPage
}

/** Tile-sized regions only: no full-size bitmap of the photograph and no screen OCR options. */
class PhotoOcrEngine @Inject constructor(private val ocr: CompositeOcr, private val device: DeviceOcr) : PhotoRecognitionEngine {
    override suspend fun open(document: PhotoDocument, language: Language, settings: PhotoOcrSettings): PhotoOcrSession {
        val engines = settings.engines
        val pinned = settings.deviceModel?.takeIf { engines != OcrEngines.CLOUD }?.let { model ->
            device.pin(language, model) ?: throw NoDeviceOcrException()
        }
        if (engines == OcrEngines.DEVICE && pinned == null) throw NoDeviceOcrException()
        var regions: PhotoRegions? = null
        try {
            withContext(Dispatchers.IO) {
                regions = try { PhotoRegions(document) } catch (_: IOException) { throw PhotoReadException() }
            }
            currentCoroutineContext().ensureActive()
            val owned = regions!!
            return object : PhotoOcrSession {
                override val settings = PhotoOcrSettings(engines, pinned?.modelId)
                override suspend fun recognize(tile: PhotoTile): OcrPage {
                    val size = fittedSize(tile.width, tile.height, maxSide = PhotoTiles.MAX_SIDE)
                    val scaled = size != tile.width to tile.height
                    val image = owned.read(tile, size = size.takeIf { scaled })
                    val page = try { ocr.recognizePhotoTile(image, language, engines, pinned?.engine) } finally { image.recycle() }
                    return if (scaled) page.scaled(tile.width, tile.height) else page
                }
                override fun close() { try { owned.close() } finally { pinned?.close() } }
            }
        } catch (e: Exception) {
            regions?.close()
            pinned?.close()
            throw e
        }
    }
}

/** A decoder keeps the original image open until all tile readers terminate, even after the document is replaced. */
internal class PhotoRegions(private val document: PhotoDocument) : AutoCloseable {
    @Suppress("DEPRECATION") // The non-shareable overload is available on minSdk 30.
    private val decoder = BitmapRegionDecoder.newInstance(document.image.path, false)
    private val mutex = Mutex()

    init {
        if (decoder.width != document.rawWidth || decoder.height != document.rawHeight) {
            decoder.recycle()
            throw PhotoReadException()
        }
    }

    /**
     * Reads [tile] upright, sampled by [sample]. With [size], an upright width and height no larger than the tile's,
     * the decoder samples by the largest power of two that keeps that size and the result is scaled to it.
     */
    suspend fun read(tile: PhotoTile, sample: Int = 1, size: Pair<Int, Int>? = null): Bitmap = mutex.withLock {
        require(tile.right <= document.width && tile.bottom <= document.height)
        require(size == null || size.first in 1..tile.width && size.second in 1..tile.height)
        var result: Bitmap? = null
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                val box = document.orientation.toRaw(tile.box, document.rawWidth, document.rawHeight)
                val sampling = size?.let { decoderSample(tile.width, tile.height, it) } ?: sample
                val raw = decoder.decodeRegion(Rect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()),
                    BitmapFactory.Options().apply { inSampleSize = sampling }) ?: throw PhotoReadException()
                try {
                    val orientation = document.orientation
                    val (x0, y0) = orientation.toOriented(0f, 0f, document.rawWidth, document.rawHeight)
                    val (x1, y1) = orientation.toOriented(1f, 0f, document.rawWidth, document.rawHeight)
                    val (x2, y2) = orientation.toOriented(0f, 1f, document.rawWidth, document.rawHeight)
                    val matrix = Matrix().apply {
                        setValues(floatArrayOf(x1 - x0, x2 - x0, 0f, y1 - y0, y2 - y0, 0f, 0f, 0f, 1f))
                    }
                    if (size != null) {
                        val upright = RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat()).also(matrix::mapRect)
                        matrix.postScale(size.first / upright.width(), size.second / upright.height())
                    }
                    result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, size != null)
                } finally {
                    if (result !== raw) raw.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            result!!
        } catch (e: Exception) {
            result?.recycle()
            throw e
        }
    }

    override fun close() = decoder.recycle()
}

internal class PhotoReadException : IOException("Cannot decode photo region")

/**
 * [width] × [height] scaled down in proportion to at most [maxSide] on each side and about [maxPixels] pixels (each
 * side rounded to whole pixels); unchanged when it fits.
 */
internal fun fittedSize(width: Int, height: Int, maxPixels: Long = Long.MAX_VALUE, maxSide: Int = Int.MAX_VALUE): Pair<Int, Int> {
    val scale = minOf(sqrt(maxPixels.toDouble() / (width.toLong() * height)), maxSide.toDouble() / maxOf(width, height))
    if (scale >= 1.0) return width to height
    fun scaled(side: Int) = (side * scale).roundToInt().coerceIn(1, minOf(side, maxSide))
    return scaled(width) to scaled(height)
}

/** The largest power-of-two decoder sample after which [width] × [height] still covers [target]. */
internal fun decoderSample(width: Int, height: Int, target: Pair<Int, Int>): Int {
    var sample = 1
    while (width / (sample * 2) >= target.first && height / (sample * 2) >= target.second) sample *= 2
    return sample
}
