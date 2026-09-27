package com.vpr.screenlate.overlay.web

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.fonts.PageFonts
import com.vpr.screenlate.overlay.settings.PopupAppearance
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The lookup page in a real WebView: rendering, appearance, the bridge and the font files it is served. */
@RunWith(AndroidJUnit4::class)
class LookupPageTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var page: LookupPage
    private val fontDirectory = File(context.filesDir, PageFonts.DIRECTORY)

    private val callbacks = object : LookupPage.Callbacks {
        override fun onClose() {
            events += "close"
        }

        override fun onLookup(query: String, primaryReading: String?) {
            events += "lookup:$query:$primaryReading"
        }

        override fun onOpenUrl(url: String) {
            events += "url:$url"
        }

        override fun onKanji(character: String) {
            events += "kanji:$character"
        }

        override fun onCopy(text: String) {
            events += "copy:$text"
        }

        override fun media(dictionary: String, path: String): ByteArray? = null
    }

    @Before
    fun setUp() = onMain {
        fontDirectory.mkdirs()
        File(fontDirectory, "probe-0.ttf").writeBytes(byteArrayOf(0, 1, 0, 0, 1, 2, 3))
        page = LookupPage(context, callbacks)
    }

    @After
    fun tearDown() = onMain {
        page.destroy()
        File(fontDirectory, "probe-0.ttf").delete()
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking { withContext(Dispatchers.Main) { block() } }

    /** Evaluates [script] once the page is ready; the result is JSON. */
    private suspend fun evaluate(script: String): String = withTimeout(TIMEOUT_MS) {
        while (true) {
            page.evaluate(script)?.let { return@withTimeout it }
            delay(POLL_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /** Waits until [script] evaluates to [expected]; scripts that start async work store their result in a global. */
    private suspend fun awaitValue(script: String, expected: String) = withTimeout(TIMEOUT_MS) {
        while (evaluate(script) != expected) delay(POLL_MS)
    }

    private fun result(expression: String, reading: String, meaning: String) = LookupResult(
        matched = expression,
        deinflected = expression,
        term = TermEntry(expression, reading, glossaries = listOf(Glossary("Test [1]", "[\"$meaning\"]"))),
    )

    @Test
    fun rendersResultsWithTheLanguageAndFonts() = onMain {
        val appearance = PageAppearance.build(
            JapaneseSupport,
            PopupAppearance(fontSize = 19, customCss = ".tag { font-family: Meiryo }"),
            emptyList(),
        )
        page.setAppearance(appearance)
        page.render(PageState.build(context, dark = true, text = "猫舌", matched = 2, results = listOf(result("猫舌", "ねこじた", "sensitive to heat"), result("猫", "ねこ", "cat")), message = null))

        awaitValue("document.querySelectorAll('article.entry').length", "2")
        assertThat(evaluate("document.documentElement.lang")).isEqualTo("\"ja\"")
        assertThat(evaluate("document.documentElement.dataset.theme")).isEqualTo("\"dark\"")
        assertThat(evaluate("getComputedStyle(document.body).fontFamily")).contains("Screenlate Sans")
        assertThat(evaluate("getComputedStyle(document.documentElement).getPropertyValue('--font-size-no-units').trim()"))
            .isEqualTo("\"19\"")
        assertThat(evaluate("document.getElementById('custom-css').textContent")).contains("Meiryo, \\\"Screenlate Sans\\\"")
        assertThat(evaluate("document.querySelector('.definition-body').textContent")).contains("sensitive to heat")
    }

    @Test
    fun bridgeCallsReachTheHost() = onMain {
        page.render(PageState.build(context, dark = false, text = "猫", matched = 1, results = listOf(result("猫", "ねこ", "cat")), message = null))
        awaitValue("document.querySelectorAll('article.entry').length", "1")
        evaluate("document.querySelector('.action-copy').click(); document.getElementById('close').click(); true")
        withTimeout(TIMEOUT_MS) { while (events.size < 2) delay(POLL_MS) }
        assertThat(events).containsExactly("copy:猫", "close").inOrder()
    }

    @Test
    fun servesInstalledFontsOnly() = onMain {
        page.render(PageState.build(context, dark = false, text = "", matched = 0, results = emptyList(), message = "-"))
        evaluate(
            """
            window.fontStatus = null;
            Promise.all(['/fonts/probe-0.ttf', '/fonts/fonts.json', '/fonts/missing-0.ttf', '/fonts/..%2Ffonts%2Fprobe-0.ttf']
                .map(path => fetch(path).then(response => response.status).catch(() => -1)))
                .then(statuses => { window.fontStatus = statuses.join(','); });
            true
            """.trimIndent(),
        )
        awaitValue("window.fontStatus !== null", "true")
        assertThat(evaluate("window.fontStatus")).isEqualTo("\"200,404,404,404\"")
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 100L
    }
}
