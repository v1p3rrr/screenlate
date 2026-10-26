package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoViewport
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual decoding and Canvas drawing without Espresso's API-dependent input injector. */
@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {
    @Test
    fun displayedPixelsAndReportedViewportAgreeForEveryExifOrientation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = File.createTempFile("photo-viewer-", ".png", instrumentation.targetContext.cacheDir)
        val original = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val points = listOf(400f to 300f, 1200f to 300f, 400f to 900f, 1200f to 900f)
        Canvas(original).apply {
            val paint = Paint()
            for (index in colors.indices) {
                paint.color = colors[index]
                val (x, y) = points[index]
                drawRect(x - 400, y - 300, x + 400, y + 300, paint)
            }
        }
        image.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        try {
            val document = mutableStateOf<PhotoDocument?>(null)
            val shown = AtomicReference<Pair<Long, PhotoViewport>?>(null)
            ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
                activity.onActivity {
                    it.setContent {
                        ScreenlateTheme {
                            document.value?.let { photo ->
                                PhotoViewer(photo) { id, viewport ->
                                    if (viewport != null) shown.set(id to viewport)
                                    else if (shown.get()?.first == id) shown.set(null)
                                }
                            }
                        }
                    }
                }
                for ((index, orientation) in PhotoOrientation.entries.withIndex()) {
                    val id = index.toLong() + 1
                    val photo = PhotoDocument(id, image, 1600, 1200, orientation, 0, 0, PhotoText.None)
                    activity.onActivity { document.value = photo }
                    val deadline = SystemClock.elapsedRealtime() + 5000
                    while (shown.get()?.first != id && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
                    assertThat(shown.get()?.first).isEqualTo(id)
                    val viewport = shown.get()!!.second
                    // The callback follows decoding; allow the scheduled Canvas frame to reach the compositor.
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(150)
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        for (pointIndex in points.indices) {
                            val raw = points[pointIndex]
                            val upright = orientation.toOriented(raw.first, raw.second, 1600, 1200)
                            val screen = (upright.first * viewport.scale + viewport.offsetX) to
                                (upright.second * viewport.scale + viewport.offsetY)
                            val actual = screenshot.getPixel(screen.first.toInt(), screen.second.toInt())
                            assertThat(actual).isEqualTo(colors[pointIndex])
                        }
                    } finally {
                        screenshot.recycle()
                    }
                }
                activity.onActivity { document.value = null }
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (shown.get() != null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
                assertThat(shown.get()).isNull()
            }
        } finally {
            image.delete()
        }
    }
}
