package com.vpr.screenlate.overlay.popup

import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.diagnostics.BackgroundDiagnostics
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.log.AppLog
import com.vpr.screenlate.core.common.log.LogFile
import com.vpr.screenlate.overlay.web.LookupPage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PopupDiagnosticsTest {
    @Test
    fun outsideTapModeRebuildRemovesOnlyTheOptionalWindowAndKeepsThePage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val real = context.getSystemService(WindowManager::class.java)
        val windows = mutableListOf<View>()
        val layouts = mutableMapOf<View, WindowManager.LayoutParams>()
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView" -> {
                    val view = args!![0] as View
                    check(view !in windows)
                    windows.add(view)
                    layouts[view] = args[1] as WindowManager.LayoutParams
                    null
                }
                "removeView" -> { check(windows.remove(args!![0] as View)); null }
                "updateViewLayout" -> {
                    val view = args!![0] as View
                    check(view in windows) { "Updating a removed window" }
                    layouts[view] = args[1] as WindowManager.LayoutParams
                    null
                }
                else -> method.invoke(real, *(args ?: emptyArray()))
            }
        } as WindowManager
        var closes = 0
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() { closes++ }
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }
        var popup: PopupController? = null
        try {
            val controller = withContext(Dispatchers.Main) {
                PopupController(context, manager, callbacks).also { popup = it }
            }
            fun show() = controller.show("{\"results\":[]}", Box(20f, 20f, 100f, 60f), null, Box(0f, 0f, 1080f, 1920f), 400f)
            val webView = withContext(Dispatchers.Main) {
                show()
                assertThat(windows).containsExactly(controller.page.container)
                controller.page.container.getChildAt(0)
            }
            withContext(Dispatchers.Main) {
                controller.hide()
                controller.detach()
                controller.hideOnOutsideTap = true
                assertThat(controller.attach()).isTrue()
                assertThat(windows).hasSize(2)
                assertThat(windows.last()).isSameInstanceAs(controller.page.container)
                show()
            }
            val catcher = withContext(Dispatchers.Main) { windows.first() }
            withTimeout(5000) {
                while (!withContext(Dispatchers.Main) {
                    catcher.visibility == View.VISIBLE && layouts.getValue(catcher).width == WindowManager.LayoutParams.MATCH_PARENT
                }) delay(20)
            }
            withContext(Dispatchers.Main) {
                fun touch(action: Int): Boolean {
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(now, now, action, 5f, 5f, 0)
                    return try { catcher.dispatchTouchEvent(event) } finally { event.recycle() }
                }
                assertThat(touch(MotionEvent.ACTION_DOWN)).isTrue()
                touch(MotionEvent.ACTION_CANCEL)
                touch(MotionEvent.ACTION_UP)
                assertThat(closes).isEqualTo(0)
                touch(MotionEvent.ACTION_DOWN)
                assertThat(touch(MotionEvent.ACTION_UP)).isTrue()
                assertThat(closes).isEqualTo(1)
                assertThat(controller.isShowing).isFalse()
                assertThat(catcher.visibility).isEqualTo(View.GONE)
                controller.detach()
                controller.hideOnOutsideTap = false
                assertThat(controller.attach()).isTrue()
                show()
                assertThat(windows).containsExactly(controller.page.container)
                assertThat(controller.page.container.getChildAt(0)).isSameInstanceAs(webView)
                controller.hide()
            }
            // A draw callback queued before hiding must not reattach or update the removed backdrop.
            delay(1100)
            withContext(Dispatchers.Main) {
                assertThat(windows).containsExactly(controller.page.container)
            }
        } finally {
            withContext(Dispatchers.Main) { popup?.release() }
        }
        assertThat(windows).isEmpty()
    }

    @Test
    fun hidingAttachedPopupRecordsClosedStateAndKeepsItsWebView() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val log = LogFile(File(context.cacheDir, "popup-diagnostics-${System.nanoTime()}"))
        AppLog.install(log)
        BackgroundDiagnostics.install(context)
        val real = context.getSystemService(WindowManager::class.java)
        val windows = mutableListOf<View>()
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView" -> { windows.add(args!![0] as View); null }
                "removeView" -> { windows.remove(args!![0] as View); null }
                "updateViewLayout" -> null
                else -> method.invoke(real, *(args ?: emptyArray()))
            }
        } as WindowManager
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() = Unit
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }
        var popup: PopupController? = null
        try {
            withContext(Dispatchers.Main) {
                val controller = PopupController(context, manager, callbacks).also { popup = it }
                controller.show("{\"results\":[]}", Box(20f, 20f, 100f, 60f), null, Box(0f, 0f, 1080f, 1920f), 400f)
                assertThat(controller.isShowing).isTrue()
                val view = controller.page.container.getChildAt(0)
                controller.hide()
                assertThat(controller.isShowing).isFalse()
                assertThat(controller.page.container.visibility).isEqualTo(View.GONE)
                assertThat(controller.page.container.getChildAt(0)).isSameInstanceAs(view)
                assertThat(windows).hasSize(1)
            }
            BackgroundDiagnostics.sampleForExport()
            val output = ByteArrayOutputStream()
            log.copyTo(output)
            val text = output.toString("UTF-8")
            assertThat(text).contains("event=popup_hidden")
            val lastSample = text.lineSequence().last { it.contains("sample=export") }
            assertThat(lastSample).contains("popup=false")
            assertThat(lastSample).contains("catcherAttached=false catcherActive=false")
            assertThat(lastSample).contains("webView=true")
        } finally {
            withContext(Dispatchers.Main) { popup?.release() }
        }
    }
}
