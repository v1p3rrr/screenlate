package com.vpr.screenlate.overlay.popup

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.web.LookupPage
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real window/input smoke; grant only the independent test package's overlay app-op before running. */
class PopupWindowLifecycleTest {
    @SdkSuppress(minSdkVersion = 30)
    @Test
    fun disabledModePassesTouchesAndEnabledModeKeepsBubbleAboveCatcher() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires the test package's overlay permission", Settings.canDrawOverlays(context))
        val real = context.getSystemService(WindowManager::class.java)
        val windows = mutableListOf<View>()
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView", "updateViewLayout" -> {
                    val view = args!![0] as View
                    val layout = WindowManager.LayoutParams().apply {
                        copyFrom(args[1] as WindowManager.LayoutParams)
                        // The independent test package has no accessibility service token.
                        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    }
                    if (method.name == "addView") {
                        real.addView(view, layout)
                        windows.add(view)
                    } else real.updateViewLayout(view, layout)
                    null
                }
                "removeView" -> {
                    val view = args!![0] as View
                    real.removeViewImmediate(view)
                    check(windows.remove(view))
                    null
                }
                else -> method.invoke(real, *(args ?: emptyArray()))
            }
        } as WindowManager
        val screen = real.currentWindowMetrics.bounds
        var belowTaps = 0
        var bubbleTaps = 0
        var closes = 0
        var popup: PopupController? = null
        var below: View? = null
        var bubble: View? = null
        try {
            withContext(Dispatchers.Main) {
                below = TextView(context).apply {
                    text = "Underlying touch target"
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.rgb(210, 235, 210))
                    setOnClickListener { belowTaps++ }
                }.also { real.addView(it, layout(screen.width(), screen.height())) }
                val callbacks = object : LookupPage.Callbacks {
                    override fun onClose() { closes++ }
                    override fun onOpenUrl(url: String) = Unit
                    override fun onKanji(character: String) = Unit
                    override fun onCopy(text: String, html: String?) = Unit
                    override fun media(dictionary: String, path: String): ByteArray? = null
                }
                popup = PopupController(context, manager, callbacks)
                assertThat(popup!!.attach()).isTrue()
                assertThat(windows).containsExactly(popup!!.page.container)
                bubble = TextView(context).apply {
                    text = "Bubble"
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.CYAN)
                    setOnClickListener { bubbleTaps++ }
                }.also { manager.addView(it, layout(140, 140).apply { x = 20; y = 180 }) }
            }
            val controller = popup!!
            fun show() = controller.show("{\"results\":[]}", Box(300f, 350f, 400f, 400f), null,
                Box(0f, 0f, screen.width().toFloat(), screen.height().toFloat()), 320f)
            suspend fun waitDrawn() = withTimeout(5000) {
                while (!withContext(Dispatchers.Main) {
                    val params = controller.page.container.layoutParams as WindowManager.LayoutParams
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
                }) delay(20)
                instrumentation.waitForIdleSync()
            }
            fun tap(x: Float, y: Float) {
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                }
                instrumentation.waitForIdleSync()
            }
            withContext(Dispatchers.Main) { show() }
            waitDrawn()
            tap(screen.width() / 2f, screen.height() - 200f)
            withContext(Dispatchers.Main) {
                assertThat(belowTaps).isEqualTo(1)
                assertThat(controller.isShowing).isTrue()
                controller.hide()
                manager.removeView(bubble)
                controller.detach()
                controller.hideOnOutsideTap = true
                controller.attach()
                manager.addView(bubble, layout(140, 140).apply { x = 20; y = 180 })
                assertThat(windows[1]).isSameInstanceAs(controller.page.container)
                assertThat(windows.last()).isSameInstanceAs(bubble)
                show()
            }
            waitDrawn()
            tap(90f, 250f)
            withContext(Dispatchers.Main) {
                assertThat(bubbleTaps).isEqualTo(1)
                assertThat(controller.isShowing).isTrue()
            }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.filesDir, "outside-tap-enabled.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            tap(screen.width() / 2f, screen.height() - 200f)
            withContext(Dispatchers.Main) {
                assertThat(closes).isEqualTo(1)
                assertThat(belowTaps).isEqualTo(1)
                assertThat(controller.isShowing).isFalse()
                manager.removeView(bubble)
                controller.detach()
                controller.hideOnOutsideTap = false
                controller.attach()
                manager.addView(bubble, layout(140, 140).apply { x = 20; y = 180 })
                assertThat(windows).containsExactly(controller.page.container, bubble).inOrder()
                show()
            }
            waitDrawn()
            tap(screen.width() / 2f, screen.height() - 200f)
            withContext(Dispatchers.Main) {
                assertThat(belowTaps).isEqualTo(2)
                assertThat(controller.isShowing).isTrue()
            }
        } finally {
            withContext(Dispatchers.Main) {
                bubble?.takeIf { it in windows }?.let { manager.removeView(it) }
                popup?.release()
                below?.let { real.removeViewImmediate(it) }
            }
        }
        assertThat(windows).isEmpty()
    }

    private fun layout(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }
}
