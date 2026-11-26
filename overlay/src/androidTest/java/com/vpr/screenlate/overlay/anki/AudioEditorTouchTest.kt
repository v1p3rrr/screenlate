package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Actual overlay and injected touch input; all displayed audio and labels are synthetic. */
@RunWith(AndroidJUnit4::class)
class AudioEditorTouchTest {
    @Test
    fun verticalScrollHorizontalPanAndDisclosedToolsRemainUsable() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() = Unit
            override fun onLookup(query: String, primaryReading: String?) = Unit
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }
        val popup = withContext(Dispatchers.Main) {
            val real = context.getSystemService(WindowManager::class.java)
            // Isolated package has no accessibility service token; preserve the real window and touch path.
            val windows = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
                if (method.name == "addView") (args[1] as WindowManager.LayoutParams).type =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                    }
                method.invoke(real, *(args ?: emptyArray()))
            } as WindowManager
            PopupController(context, windows, callbacks)
        }
        val commands = mutableListOf<String>()
        suspend fun js(script: String): String = withContext(Dispatchers.Main) {
            withTimeout(5000) {
                var value = popup.page.evaluate(script)
                while (value == null) { delay(10); value = popup.page.evaluate(script) }
                value
            }
        }
        suspend fun until(script: String) = withTimeout(5000) {
            while (js(script) != "true") delay(20)
        }
        suspend fun point(id: String, xFraction: Float = 0.5f): Pair<Float, Float> {
            val values = JSONArray(JSONArray("[" + js("JSON.stringify((()=>{const el=document.getElementById('$id'),r=el.getBoundingClientRect(),body=el.closest('.sl-editor-content'),b=body?body.getBoundingClientRect():r;return [r.x+r.width*$xFraction,(Math.max(r.top,b.top)+Math.min(r.bottom,b.bottom))/2,innerWidth]})())") + "]").getString(0))
            return withContext(Dispatchers.Main) {
                val location = IntArray(2)
                popup.page.container.getLocationOnScreen(location)
                val scale = popup.page.container.width.toFloat() / values.getDouble(2).toFloat()
                location[0] + values.getDouble(0).toFloat() * scale to location[1] + values.getDouble(1).toFloat() * scale
            }
        }
        fun input(action: Int, x: Float, y: Float, down: Long) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrument.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        suspend fun swipe(start: Pair<Float, Float>, dx: Float, dy: Float) {
            val down = SystemClock.uptimeMillis()
            input(MotionEvent.ACTION_DOWN, start.first, start.second, down)
            for (i in 1..10) { delay(20); input(MotionEvent.ACTION_MOVE, start.first + dx*i/10, start.second + dy*i/10, down) }
            delay(120)
            input(MotionEvent.ACTION_UP, start.first + dx, start.second + dy, down)
            delay(200)
        }
        suspend fun tap(id: String) {
            val p = point(id); val down = SystemClock.uptimeMillis()
            input(MotionEvent.ACTION_DOWN, p.first, p.second, down)
            input(MotionEvent.ACTION_UP, p.first, p.second, down)
            delay(100)
        }
        fun snapshot(name: String) {
            val bitmap = checkNotNull(instrument.uiAutomation.takeScreenshot())
            try { File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        try {
            withContext(Dispatchers.Main) {
                val metrics = context.resources.displayMetrics
                popup.show(PageState.build(context, PageTheme.DARK, "", 0, emptyList(), "-", language = Language.JAPANESE),
                    Box(50f,50f,100f,100f), null, Box(0f,0f,metrics.widthPixels.toFloat(),metrics.heightPixels.toFloat()), metrics.widthPixels.toFloat())
                popup.enterAudioEditor()
                popup.page.audioEditorActions = object : LookupPage.AudioEditorActions {
                    override fun onAudioEditorCommand(payload: String) { commands += JSONObject(payload).getString("action") }
                }
                popup.page.openAudioEditor("""{"operation":"touch","totalFrames":2880000,"selection":[960000,1200000],"overviewSummary":[0.2,0.7,0.3,0.8]}""")
            }
            until("typeof AudioEditor!=='undefined' && AudioEditor.state.isOpen")
            assertThat(js("document.getElementById('sl-tools').open")).isEqualTo("false")
            assertThat(js("getComputedStyle(document.getElementById('sl-suggestions')).display==='none'")).isEqualTo("true")
            withTimeout(5000) { while (!withContext(Dispatchers.Main) { popup.page.container.alpha > 0f }) delay(20) }
            delay(600)
            val initialGeometry = js("JSON.stringify((()=>{const wave=document.querySelector('.sl-wave-wrap').getBoundingClientRect(),body=document.querySelector('.sl-editor-content').getBoundingClientRect();return {wave:{top:wave.top,bottom:wave.bottom,height:wave.height},body:{top:body.top,bottom:body.bottom,height:body.height},viewport:[innerWidth,innerHeight],scroll:document.querySelector('.sl-editor-content').scrollTop}})())")
            com.google.common.truth.Truth.assertWithMessage("Initial editor geometry: %s", initialGeometry)
                .that(js("(()=>{const wave=document.querySelector('.sl-wave-wrap').getBoundingClientRect(),body=document.querySelector('.sl-editor-content').getBoundingClientRect();return wave.top>=body.top && wave.bottom<=body.bottom})()" ))
                .isEqualTo("true")
            val view = js("AudioEditor.state.view")
            val cursor = js("AudioEditor.state.cursor")
            swipe(point("sl-wave"), 8f, -100f)
            until("document.querySelector('.sl-editor-content').scrollTop>0")
            assertThat(js("AudioEditor.state.view")).isEqualTo(view)
            assertThat(js("AudioEditor.state.cursor")).isEqualTo(cursor)
            js("document.querySelector('.sl-wave-wrap').scrollIntoView({block:'end'}); true")
            delay(100)
            val beforeBarScroll = js("document.querySelector('.sl-editor-content').scrollTop").toDouble()
            swipe(point("sl-playhead", 0.95f), 0f, -100f)
            assertThat(js("document.querySelector('.sl-editor-content').scrollTop").toDouble()).isGreaterThan(beforeBarScroll)
            assertThat(js("AudioEditor.state.cursor")).isEqualTo(cursor)
            js("document.querySelector('.sl-editor-content').scrollTop=0; true")
            delay(100)
            swipe(point("sl-wave"), -80f, 2f)
            assertThat(js("AudioEditor.state.view")).isNotEqualTo(view)
            js("document.querySelector('.sl-wave-wrap').scrollIntoView({block:'end'}); AudioEditor.updateCursor(Math.round(AudioEditor.state.view+AudioEditor.state.width/2)); true")
            delay(100)
            val dotCursor = js("AudioEditor.state.cursor")
            val dotView = js("AudioEditor.state.view")
            swipe(point("sl-playhead"), 50f, 0f)
            assertThat(js("AudioEditor.state.cursor")).isNotEqualTo(dotCursor)
            assertThat(js("AudioEditor.state.view")).isEqualTo(dotView)
            tap("sl-action-play")
            assertThat(commands).contains("play")
            js("document.getElementById('sl-tools-summary').scrollIntoView({block:'nearest'}); true")
            tap("sl-tools-summary")
            until("document.getElementById('sl-tools').open")
            js("document.getElementById('sl-action-cut').scrollIntoView({block:'nearest'}); true")
            tap("sl-action-cut")
            until("AudioEditor.state.cuts.length===1")
            js("document.getElementById('sl-action-undo').scrollIntoView({block:'nearest'}); true")
            tap("sl-action-undo")
            until("AudioEditor.state.cuts.length===0")
            js("document.getElementById('sl-tools').open=false; document.querySelector('.sl-editor-content').scrollTop=0; true")
            delay(200)
            snapshot("touch-compact.png")
            withContext(Dispatchers.Main) { popup.setAudioEditorExpanded(true) }
            delay(150)
            snapshot("touch-expanded.png")
        } finally { withContext(Dispatchers.Main) { popup.release() } }
    }
}
