package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.view.View
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
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView with a recording WindowManager; resizing never recreates the editor or its host. */
@RunWith(AndroidJUnit4::class)
class AudioEditorResizeTest {
    @Test
    fun compactExpandedAndCollapsedHostsKeepTheirDraftAcrossResize() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val windows = mutableListOf<View>()
        var additions = 0
        var removals = 0
        val flags = mutableMapOf<View, Int>()
        val real = context.getSystemService(WindowManager::class.java)
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView" -> { windows += args[0] as View; additions++; null }
                "removeView" -> { windows -= args[0] as View; removals++; null }
                "updateViewLayout" -> {
                    val view = args[0] as View
                    val params = args[1] as WindowManager.LayoutParams
                    flags[view] = params.flags
                    view.measure(View.MeasureSpec.makeMeasureSpec(params.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(params.height.coerceAtLeast(1), View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, params.width.coerceAtLeast(1), params.height.coerceAtLeast(1))
                    null
                }
                else -> method.invoke(real, *(args ?: emptyArray()))
            }
        } as WindowManager
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() = Unit
            override fun onLookup(query: String, primaryReading: String?) = Unit
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }
        val popup = PopupController(context, manager, callbacks)
        try {
            val portrait = Box(0f, 0f, 1080f, 1920f)
            val landscape = Box(0f, 0f, 1920f, 1080f)
            popup.show(PageState.build(context, PageTheme.DARK, "", 0, emptyList(), "-", language = Language.JAPANESE),
                Box(100f, 100f, 200f, 200f), null, portrait, 1000f)
            val page = popup.page
            suspend fun evaluate(script: String): String = withTimeout(5000) {
                while (true) {
                    page.evaluate(script)?.let { return@withTimeout it }
                    delay(1)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            withTimeout(5000) { while (evaluate("typeof AudioEditor") != "\"object\"") delay(1) }
            popup.enterAudioEditor()
            assertThat(flags[page.container]!! and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).isEqualTo(0)
            assertThat(flags[page.container]!! and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).isNotEqualTo(0)
            page.openAudioEditor("""{"operation":"resize","totalFrames":5280000,"sourceStart":4800000,"sourceEnd":5280000,"selection":[4800000,5280000],"cuts":[[4950000,4980000]]}""")
            withTimeout(5000) { while (evaluate("AudioEditor.state.isOpen") != "true") delay(1) }
            evaluate("document.getElementById('sl-start').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true})); document.getElementById('sl-end').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true})); true")
            assertThat(evaluate("AudioEditor.state.a")).isEqualTo("4800000")
            assertThat(evaluate("AudioEditor.state.b")).isEqualTo("5280000")
            evaluate("document.getElementById('sl-action-zoom-out').click(); true")
            assertThat(evaluate("AudioEditor.state.width <= 480000 && AudioEditor.state.view >= 4800000")).isEqualTo("true")
            evaluate("AudioEditor.state.width=200000; AudioEditor.state.view=4850000; AudioEditor.state.cursor=4920000; AudioEditor.state.mode='original'; true")
            val snapshot = "JSON.stringify([AudioEditor.state.operation,AudioEditor.state.a,AudioEditor.state.b,AudioEditor.state.cuts,AudioEditor.state.width,AudioEditor.state.view,AudioEditor.state.cursor,AudioEditor.state.mode])"
            val draft = evaluate(snapshot)
            for (size in listOf(landscape, portrait)) {
                popup.relayout(size, 1000f)
                assertThat(evaluate(snapshot)).isEqualTo(draft)
                assertThat(popup.bounds!!.right).isAtMost(size.right)
                assertThat(popup.bounds!!.bottom).isAtMost(size.bottom)
                popup.setAudioEditorExpanded(true)
                popup.relayout(size, 1000f)
                assertThat(evaluate(snapshot)).isEqualTo(draft)
                popup.setAudioEditorCollapsed(true)
                popup.relayout(size, 1000f)
                assertThat(evaluate(snapshot)).isEqualTo(draft)
                popup.setAudioEditorCollapsed(false)
            }
            assertThat(additions).isEqualTo(2)
            assertThat(removals).isEqualTo(0)
            assertThat(popup.isAudioEditorActive).isTrue()
            popup.resetAudioEditorPresentation()
            assertThat(flags[page.container]!! and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).isNotEqualTo(0)
        } finally { popup.release() }
    }
}
