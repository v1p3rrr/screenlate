package com.vpr.screenlate.audio

import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tracing.Trace
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.audio.SentenceAudioStaging
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.overlay.anki.AudioEditorController
import com.vpr.screenlate.overlay.anki.AudioEditorResult
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production WebView bridge, command serialization, and sentence-media provider, also runnable against a minified app. */
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

    @Test
    fun audioEditorControllerDeserializesBridgeCommandsWithoutExtraKeepRules() = runBlocking {
        Trace.beginSection("MinifiedSentenceAudioControllerTest")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val realWm = context.getSystemService(WindowManager::class.java)
        val proxyWm = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, m, args ->
            when (m.name) {
                "addView", "updateViewLayout", "removeView", "removeViewImmediate" -> null
                else -> m.invoke(realWm, *(args ?: emptyArray()))
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
        val settings = RecordingSettingsRepository(Store())
        val recorder = SentenceRecorder(context, settings, PlaybackCoordinator())
        val popup = withContext(Dispatchers.Main) { PopupController(context, proxyWm, callbacks) }
        var controller: AudioEditorController? = null
        try {
            assertTrue(recorder.start(SilentSource()))
            delay(150)
            val scan = checkNotNull(recorder.acquireScan())
            withContext(Dispatchers.Main) {
                popup.attach()
                popup.show(
                    PageState.build(context, PageTheme.DARK, "", 0, emptyList(), "-", language = Language.JAPANESE),
                    Box(100f, 100f, 200f, 200f),
                    null,
                    Box(0f, 0f, 1080f, 1920f),
                    900f,
                )
            }
            val editor = withContext(Dispatchers.Main) {
                AudioEditorController(
                    context,
                    popup.page,
                    popup,
                    recorder,
                    settings,
                    {},
                    {},
                    SentenceAudioStaging(context),
                )
            }.also { controller = it }

            val pending = async(Dispatchers.Main) {
                editor.edit(scan, "言葉", "ことば", false) { false }
            }
            withContext(Dispatchers.Main) {
                awaitValue(popup.page, "AudioEditor.state.isOpen && AudioEditor.state.phase === 'editing'", "true")
                evaluate(
                    popup.page,
                    "document.getElementById('sl-action-cut').click(); document.getElementById('sl-action-without').click(); true",
                )
            }
            val result = withTimeout(TIMEOUT_MILLIS) { pending.await() }
            assertEquals(AudioEditorResult.WithoutAudio, result)
        } finally {
            withContext(Dispatchers.Main) {
                controller?.release()
                popup.release()
            }
            recorder.close()
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

    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class SilentSource : AudioSource {
        override val sampleRate = 48_000
        override val channelCount = 1
        private var remaining = sampleRate * 2 * 2
        private val stopped = CountDownLatch(1)
        private var origin = 0L
        override fun start(): Boolean { origin = SystemClock.elapsedRealtimeNanos(); return true }
        override fun timestampNanos() = origin
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) { stopped.await(); return -1 }
            val count = minOf(length, remaining)
            remaining -= count
            return count
        }
        override fun stop() { stopped.countDown() }
        override fun close() { stopped.countDown() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
