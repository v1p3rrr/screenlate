package com.vpr.screenlate.overlay.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.vpr.screenlate.core.common.log.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Screen capture backend for Android 7.0–10 (API 24–29) using MediaProjection, VirtualDisplay, and ImageReader.
 */
class LegacyMediaProjectionCapturer(
    private val service: AccessibilityService,
) : ScreenCapturer {

    override suspend fun capture(
        pointX: Int,
        pointY: Int,
        withoutOverlays: suspend (displayCapture: suspend () -> CapturedScreen) -> CapturedScreen,
    ): CapturedScreen {
        val started = SystemClock.elapsedRealtime()
        return withoutOverlays { captureDisplay() }.also { log("display", it, started) }
    }

    private fun log(kind: String, shot: CapturedScreen, started: Long) {
        Log.d(TAG, "Captured the $kind, ${shot.bitmap.width}x${shot.bitmap.height}, in ${SystemClock.elapsedRealtime() - started} ms")
    }

    private suspend fun captureDisplay(): CapturedScreen = withContext(Dispatchers.Default) {
        val projection = MediaProjectionHolder.getOrCreate(service)
            ?: throw CaptureException.ConsentDenied()

        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenlateCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null,
            )

            val image = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                waitForImage(imageReader)
            } ?: throw CaptureException.Failed(-1)
            val bitmap = imageToBitmap(image, width, height)
            CapturedScreen(bitmap, 0, 0)
        } finally {
            virtualDisplay?.release()
            imageReader.close()
        }
    }

    private suspend fun waitForImage(imageReader: ImageReader): Image =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var resumed = false
            val listener = ImageReader.OnImageAvailableListener { reader ->
                val img = try {
                    reader.acquireLatestImage()
                } catch (_: Exception) {
                    null
                }
                if (img != null && !resumed) {
                    resumed = true
                    continuation.resume(img)
                }
            }
            imageReader.setOnImageAvailableListener(listener, handler)
            continuation.invokeOnCancellation {
                imageReader.setOnImageAvailableListener(null, null)
            }
        }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            return if (rowPadding == 0) {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                bmp
            } else {
                val tempBmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                tempBmp.copyPixelsFromBuffer(buffer)
                val bmp = Bitmap.createBitmap(tempBmp, 0, 0, width, height)
                tempBmp.recycle()
                bmp
            }
        } finally {
            image.close()
        }
    }

    private companion object {
        const val TAG = "LegacyMediaProjectionCapturer"
        const val CAPTURE_TIMEOUT_MS = 2500L
    }
}
