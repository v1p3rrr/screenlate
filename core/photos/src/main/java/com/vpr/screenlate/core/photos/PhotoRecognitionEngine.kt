package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile
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

/** Opens fixed settings and image ownership for one multi-tile attempt. [fixed] is used for a failed-tile retry. */
interface PhotoRecognitionEngine {
    suspend fun open(document: PhotoDocument, language: Language, engines: OcrEngines, fixed: PhotoOcrSettings? = null): PhotoOcrSession
}

interface PhotoOcrSession : AutoCloseable {
    val settings: PhotoOcrSettings
    suspend fun recognize(tile: PhotoTile): OcrPage
}

/** Original-resolution regions only: no full-size bitmap of the photograph and no screen OCR options. */
class PhotoOcrEngine @Inject constructor(private val ocr: CompositeOcr, private val device: DeviceOcr) : PhotoRecognitionEngine {
    override suspend fun open(document: PhotoDocument, language: Language, engines: OcrEngines, fixed: PhotoOcrSettings?): PhotoOcrSession {
        val pinned = when {
            engines == OcrEngines.CLOUD -> null
            fixed == null -> device.pin(language)
            fixed.deviceModel == null -> null
            else -> device.pin(language, fixed.deviceModel) ?: throw NoDeviceOcrException()
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
                    val image = owned.read(tile)
                    try { return ocr.recognizePhotoTile(image, language, engines, pinned?.engine) }
                    finally { image.recycle() }
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

    suspend fun read(tile: PhotoTile, sample: Int = 1): Bitmap = mutex.withLock {
        require(tile.right <= document.width && tile.bottom <= document.height)
        var result: Bitmap? = null
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                val box = document.orientation.toRaw(tile.box, document.rawWidth, document.rawHeight)
                val raw = decoder.decodeRegion(Rect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()),
                    BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw PhotoReadException()
                try {
                    val orientation = document.orientation
                    val (x0, y0) = orientation.toOriented(0f, 0f, document.rawWidth, document.rawHeight)
                    val (x1, y1) = orientation.toOriented(1f, 0f, document.rawWidth, document.rawHeight)
                    val (x2, y2) = orientation.toOriented(0f, 1f, document.rawWidth, document.rawHeight)
                    val matrix = Matrix().apply {
                        setValues(floatArrayOf(x1 - x0, x2 - x0, 0f, y1 - y0, y2 - y0, 0f, 0f, 0f, 1f))
                    }
                    result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false)
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
