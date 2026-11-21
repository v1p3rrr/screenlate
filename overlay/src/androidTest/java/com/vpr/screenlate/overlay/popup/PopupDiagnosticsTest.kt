package com.vpr.screenlate.overlay.popup

import android.view.View
import android.view.WindowManager
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
import org.junit.Test

class PopupDiagnosticsTest {
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
                assertThat(windows).hasSize(2)
            }
            BackgroundDiagnostics.sampleForExport()
            val output = ByteArrayOutputStream()
            log.copyTo(output)
            val text = output.toString("UTF-8")
            assertThat(text).contains("event=popup_hidden")
            val lastSample = text.lineSequence().last { it.contains("sample=export") }
            assertThat(lastSample).contains("popup=false")
            assertThat(lastSample).contains("catcherAttached=true catcherActive=false")
            assertThat(lastSample).contains("webView=true")
        } finally {
            withContext(Dispatchers.Main) { popup?.release() }
        }
    }
}
