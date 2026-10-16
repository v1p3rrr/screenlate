package com.vpr.screenlate.languages

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.overlay.OverlayIntents
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import com.vpr.screenlate.overlay.R as OverlayR
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallationPopupTest {
    @Test
    fun blockedScanShowsAnInstallationLinkInThePopup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val opened = AtomicReference<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
            lateinit var page: LookupPage
            activity.onActivity { screen ->
                page = LookupPage(screen, object : LookupPage.Callbacks {
                    override fun onClose() = Unit
                    override fun onLookup(query: String, primaryReading: String?) = Unit
                    override fun onOpenUrl(url: String) { opened.set(url) }
                    override fun onKanji(character: String) = Unit
                    override fun onCopy(text: String, html: String?) = Unit
                    override fun media(dictionary: String, path: String): ByteArray? = null
                })
                screen.setContentView(page.container)
                page.render(PageState.build(screen, PageTheme.DARK, "", 0, emptyList(), screen.getString(OverlayR.string.installation_running), installation = true, language = Language.JAPANESE))
            }
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (opened.get() == null && SystemClock.elapsedRealtime() < deadline) {
                activity.onActivity { screen ->
                    webView(screen.window.decorView)?.evaluateJavascript("document.querySelector('.installation-status')?.click()", null)
                }
                SystemClock.sleep(50)
            }
            assertEquals(OverlayIntents.INSTALLATION_URL, opened.get())
            // The JS bridge can answer before the emulator's WebView commits its first painted frame.
            SystemClock.sleep(500)
            val image = instrumentation.uiAutomation.takeScreenshot()
            assertTrue(image != null)
            File(instrumentation.targetContext.filesDir, "installation-popup-test.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            activity.onActivity { page.destroy() }
        }
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }
}
