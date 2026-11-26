package com.vpr.screenlate.audio

import android.os.Looper
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tracing.Trace
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production WebView bridge and sentence-media provider, also runnable against a minified app. */
@RunWith(AndroidJUnit4::class)
class MinifiedSentenceAudioDeviceTest {
    @Test
    fun sentenceAudioBridgeAndProviderRemainUsable() = runBlocking {
        Trace.beginSection("MinifiedSentenceAudioDeviceTest")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = byteArrayOf(0, 1, 2, 3, 127, -128)
        val file = File(AnkiDroid.mediaDirectory(context), "screenlate_sentence_minified.opus")
        var page: LookupPage? = null
        try {
            withContext(Dispatchers.IO) { file.writeBytes(expected) }
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + AnkiDroid.AUTHORITY_SUFFIX,
                file,
            )
            val actual = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            }
            assertArrayEquals(expected, actual)

            withContext(Dispatchers.Main) {
                val commands = mutableListOf<String>()
                val callbacks = object : LookupPage.Callbacks {
                    override fun onClose() = Unit
                    override fun onLookup(query: String, primaryReading: String?) = Unit
                    override fun onOpenUrl(url: String) = Unit
                    override fun onKanji(character: String) = Unit
                    override fun onCopy(text: String, html: String?) = Unit
                    override fun media(dictionary: String, path: String): ByteArray? = null
                }
                val created = LookupPage(context, callbacks).also { page = it }
                created.audioEditorActions = object : LookupPage.AudioEditorActions {
                    override fun onAudioEditorCommand(payload: String) {
                        check(Looper.myLooper() === Looper.getMainLooper())
                        commands += payload
                    }
                }
                created.render(
                    PageState.build(
                        context,
                        theme = PageTheme.DARK,
                        text = "",
                        matched = 0,
                        results = emptyList(),
                        message = "-",
                        language = Language.JAPANESE,
                    ),
                )
                created.openAudioEditor(
                    """{"operation":"minified-fixture","sourceRevision":0,"totalFrames":480000,"selection":[48000,240000]}""",
                )
                awaitValue(created, "AudioEditor.state.isOpen", "true")
                evaluate(
                    created,
                    "document.getElementById('sl-action-cut').click(); document.getElementById('sl-action-add').click(); true",
                )
                withTimeout(TIMEOUT_MILLIS) {
                    while (commands.none { it.contains("\"action\":\"save\"") }) delay(POLL_MILLIS)
                }
                assertTrue(commands.last().contains("\"operation\":\"minified-fixture\""))
                assertFalse(commands.last().contains("\"exclusions\":[]"))

                created.audioEditorMessage(
                    """{"operation":"minified-fixture","phase":"failed","error":"Fixture export failure"}""",
                )
                awaitValue(created, "document.getElementById('sl-action-add').disabled", "false")
                created.audioEditorMessage("""{"operation":"minified-fixture","closed":true}""")
                awaitValue(created, "AudioEditor.state.isOpen", "false")
            }
        } finally {
            withContext(Dispatchers.Main) { page?.destroy() }
            withContext(Dispatchers.IO) { file.delete() }
            Trace.endSection()
        }
    }

    private suspend fun evaluate(page: LookupPage, script: String): String = withTimeout(TIMEOUT_MILLIS) {
        while (true) {
            page.evaluate(script)?.let { return@withTimeout it }
            delay(POLL_MILLIS)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun awaitValue(page: LookupPage, script: String, expected: String) {
        withTimeout(TIMEOUT_MILLIS) {
            while (evaluate(page, script) != expected) delay(POLL_MILLIS)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
