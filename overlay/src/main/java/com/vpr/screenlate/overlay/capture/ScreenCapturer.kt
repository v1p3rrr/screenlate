package com.vpr.screenlate.overlay.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A screenshot and where it sits on the screen.
 *
 * @property left x of the bitmap's top-left corner in screen coordinates.
 */
class CapturedScreen(val bitmap: Bitmap, val left: Int, val top: Int)

sealed class CaptureException(message: String) : Exception(message) {
    class SecureWindow : CaptureException("The app blocks screenshots")
    class Failed(code: Int) : CaptureException("Screenshot failed with code $code")
}

/**
 * Takes screenshots through the accessibility service.
 *
 * On API 34+ only the app window under the given point is captured, so our own overlays never appear in the image.
 * Older versions capture the whole display; the caller hides the overlays for that case (see [needsOverlayHiding]).
 */
class ScreenCapturer(
    private val service: AccessibilityService,
    private val executor: Executor,
) {
    val needsOverlayHiding: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    suspend fun capture(pointX: Int, pointY: Int): CapturedScreen {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val window = findAppWindow(pointX, pointY)
            if (window != null) {
                val bounds = Rect().also(window::getBoundsInScreen)
                runCatching { return captureWindow(window.id, bounds) }
                    .onFailure { if (it is CaptureException.SecureWindow) throw it }
                    .onFailure { Log.w(TAG, "Window capture failed, falling back to display capture", it) }
            }
        }
        return captureDisplay()
    }

    private fun findAppWindow(x: Int, y: Int): AccessibilityWindowInfo? {
        val appWindows = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val bounds = Rect()
        return appWindows
            .filter { window -> window.getBoundsInScreen(bounds); bounds.contains(x, y) }
            .maxByOrNull { it.layer }
            ?: appWindows.firstOrNull { it.isActive }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun captureWindow(windowId: Int, bounds: Rect): CapturedScreen =
        withIntervalRetry { callback -> service.takeScreenshotOfWindow(windowId, executor, callback) }
            .let { CapturedScreen(it, bounds.left, bounds.top) }

    private suspend fun captureDisplay(): CapturedScreen =
        withIntervalRetry { callback -> service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback) }
            .let { CapturedScreen(it, 0, 0) }

    /** The system rejects screenshots taken less than ~333 ms apart; retry once after waiting. */
    private suspend fun withIntervalRetry(request: (TakeScreenshotCallback) -> Unit): Bitmap {
        repeat(2) { attempt ->
            when (val result = takeOnce(request)) {
                is Result.Success -> return result.bitmap
                is Result.Error -> when {
                    result.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && attempt == 0 ->
                        delay(INTERVAL_MS)
                    result.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                        throw CaptureException.SecureWindow()
                    else -> throw CaptureException.Failed(result.code)
                }
            }
        }
        throw CaptureException.Failed(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)
    }

    private suspend fun takeOnce(request: (TakeScreenshotCallback) -> Unit): Result =
        suspendCancellableCoroutine { continuation ->
            request(
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        continuation.resume(if (bitmap != null) Result.Success(bitmap) else Result.Error(-1))
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(Result.Error(errorCode))
                    }
                },
            )
        }

    private sealed interface Result {
        class Success(val bitmap: Bitmap) : Result
        class Error(val code: Int) : Result
    }

    private companion object {
        const val TAG = "ScreenCapturer"
        const val INTERVAL_MS = 350L
    }
}
