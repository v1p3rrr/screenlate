package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.os.SystemClock
import android.view.WindowManager
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Production editor, bridge, PCM facade and codec in an isolated package; no Anki or system capture. */
@RunWith(AndroidJUnit4::class)
class AudioEditorDeviceTest {
    @Test
    fun exportOwnsItsEditAndOnlySuccessfulNoteConfirmationReusesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RecordingSettingsRepository(Store())
        val recorder = SentenceRecorder(context, settings, PlaybackCoordinator())
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() = Unit
            override fun onLookup(query: String, primaryReading: String?) = Unit
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }
        val popup = withContext(Dispatchers.Main) {
            PopupController(context, context.getSystemService(WindowManager::class.java), callbacks)
        }
        val page = popup.page
        var result: AudioEditorResult.WithAudio? = null
        try {
            assertThat(recorder.start(SilentSource())).isTrue()
            delay(150)
            val scan = checkNotNull(recorder.acquireScan())
            withContext(Dispatchers.Main) {
                page.render(PageState.build(context, theme = PageTheme.DARK, text = "", matched = 0,
                    results = emptyList(), message = "-", language = Language.JAPANESE))
                val controller = AudioEditorController(context, page, popup, recorder, settings)
                suspend fun evaluate(script: String): String = withTimeout(5000) {
                    while (true) {
                        page.evaluate(script)?.let { return@withTimeout it }
                        delay(1)
                    }
                    @Suppress("UNREACHABLE_CODE") error("unreachable")
                }
                suspend fun ready() = withTimeout(5000) {
                    while (evaluate("typeof AudioEditor !== 'undefined' && AudioEditor.state.isOpen && AudioEditor.state.phase === 'editing'") != "true") delay(1)
                }
                val pending = async { controller.edit(scan, "", "") }
                ready()
                val initialEnd = evaluate("AudioEditor.state.b").toLong()
                assertThat(initialEnd).isGreaterThan(100L)
                evaluate("AudioEditor.state.b = Math.floor(AudioEditor.state.b / 2); true")
                assertThat(controller.requestSave()).isTrue()
                result = withTimeout(10_000) { pending.await() } as AudioEditorResult.WithAudio
                assertThat(result!!.edit.selection.endExclusive).isEqualTo(initialEnd / 2)
                assertThat(result!!.file.length()).isGreaterThan(0L)
                assertThat(evaluate("AudioEditor.state.isOpen")).isEqualTo("false")

                // Export alone must not promote the remembered selection.
                val abandoned = async { controller.edit(scan, "", "") }
                ready()
                assertThat(evaluate("AudioEditor.state.b").toLong()).isEqualTo(initialEnd)
                evaluate("document.getElementById('sl-action-without').click(); true")
                assertThat(withTimeout(5000) { abandoned.await() }).isEqualTo(AudioEditorResult.WithoutAudio)

                controller.confirm(result!!)
                val confirmed = async { controller.edit(scan, "", "") }
                ready()
                assertThat(evaluate("AudioEditor.state.b").toLong()).isEqualTo(initialEnd / 2)
                page.container.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                page.container.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                assertThat(withTimeout(5000) { confirmed.await() }).isEqualTo(AudioEditorResult.Cancelled)

                val stopping = async { controller.edit(scan, "", "") }
                ready()
                controller.release()
                page.destroy()
                assertThat(withTimeout(5000) { stopping.await() }).isEqualTo(AudioEditorResult.Cancelled)
                assertThat(page.container.childCount).isEqualTo(0)
            }
        } finally {
            recorder.close()
            withContext(Dispatchers.IO) { result?.file?.delete() }
            withContext(Dispatchers.Main) { page.destroy() }
        }
    }

    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class SilentSource : AudioSource {
        override val sampleRate = 48000
        override val channelCount = 1
        private var remaining = sampleRate * 2 * 2
        private val stopped = CountDownLatch(1)
        private var origin = 0L
        override fun start(): Boolean { origin = SystemClock.elapsedRealtimeNanos(); return true }
        override fun timestampNanos() = origin
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) { stopped.await(); return -1 }
            val count = minOf(length, remaining)
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
        override fun stop() { stopped.countDown() }
        override fun close() { stopped.countDown() }
    }
}
