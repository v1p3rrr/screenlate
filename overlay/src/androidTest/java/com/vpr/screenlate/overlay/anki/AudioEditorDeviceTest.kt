package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.os.SystemClock
import android.view.WindowManager
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.AudioRange
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Production editor, bridge, PCM facade and codec in an isolated package; no Anki or system capture. */
@RunWith(AndroidJUnit4::class)
class AudioEditorDeviceTest {
    @Test
    fun sleepingDisplayRejectsOpeningWithoutStrandingEditorJobs() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
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
        var controller: AudioEditorController? = null
        suspend fun screenKey(key: Int) = withContext(Dispatchers.IO) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrument.uiAutomation.executeShellCommand("input keyevent $key")
            ).use { it.readBytes() }
        }
        try {
            assertThat(recorder.start(SilentSource())).isTrue()
            delay(150)
            val scan = checkNotNull(recorder.acquireScan())
            screenKey(223)
            val power = context.getSystemService(android.os.PowerManager::class.java)
            withTimeout(3000) { while (power.isInteractive) delay(1) }
            withContext(Dispatchers.Main) {
                val editor = AudioEditorController(context, popup.page, popup, recorder, settings)
                controller = editor
                assertThat(withTimeout(3000) { editor.edit(scan, "", "") }).isEqualTo(AudioEditorResult.Cancelled)
                assertThat(popup.isAudioEditorActive).isFalse()
            }
        } finally {
            withContext(Dispatchers.Main) { controller?.release(); popup.release() }
            recorder.close()
            screenKey(224)
        }
    }

    @Test
    fun firstSelectionUsesSpeechThatArrivesWhileThePageIsOpening() = openingSpeechScenario(false)

    @Test
    fun delayedInitialRefinementCannotReplaceAnInFlightEdit() = openingSpeechScenario(true)

    private fun openingSpeechScenario(inFlightEdit: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RecordingSettingsRepository(Store())
        val recorder = SentenceRecorder(context, settings, PlaybackCoordinator())
        val source = QuietThenSpeechSource()
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
        try {
            assertThat(recorder.start(source)).isTrue()
            delay(7200)
            val scan = checkNotNull(recorder.acquireScan())
            withContext(Dispatchers.Main) {
                val page = popup.page
                page.render(PageState.build(context, PageTheme.DARK, "", 0, emptyList(), "-", language = Language.JAPANESE))
                val controller = AudioEditorController(context, page, popup, recorder, settings)
                val ready = CompletableDeferred<String>()
                val heldEdit = CompletableDeferred<String>()
                page.audioEditorActions = object : LookupPage.AudioEditorActions {
                    override fun onAudioEditorCommand(payload: String) {
                        when (JSONObject(payload).optString("action")) {
                            "ready" -> ready.complete(payload)
                            "edit" -> if (inFlightEdit && !heldEdit.isCompleted) heldEdit.complete(payload)
                                else controller.onAudioEditorCommand(payload)
                            else -> controller.onAudioEditorCommand(payload)
                        }
                    }
                }
                suspend fun startFrame(): Long = page.evaluate("AudioEditor.state.a")!!.toLong()
                try {
                    val pending = async { controller.edit(scan, "", "") }
                    val command = withTimeout(5000) { ready.await() }
                    assertThat(startFrame()).isEqualTo(0)
                    source.speech.countDown()
                    withTimeout(5000) {
                        while (recorder.waveformSummary(AudioRange(source.quietFrames, source.quietFrames + 48000), contextId = scan) == null) delay(1)
                    }
                    delay(1000)
                    if (inFlightEdit) page.evaluate("""
                        window.sawInitialRefinement=false;
                        var originalReceive=AudioEditor.receive;
                        AudioEditor.receive=function(data){
                            if(data.initialSelection && !data.phase) window.sawInitialRefinement=true;
                            originalReceive(data);
                            if(data.phase==='editing' && !window.editedOnReady){
                                window.editedOnReady=true;
                                AudioEditor.state.a=96000;
                                document.getElementById('sl-start').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));
                            }
                        }; true
                    """.trimIndent())
                    controller.onAudioEditorCommand(command)
                    if (inFlightEdit) {
                        withTimeout(5000) { while (page.evaluate("window.sawInitialRefinement===true") != "true") delay(1) }
                        assertThat(startFrame()).isEqualTo(100800)
                        controller.onAudioEditorCommand(withTimeout(5000) { heldEdit.await() })
                        controller.requestSave()
                        val result = withTimeout(10000) { pending.await() } as AudioEditorResult.WithAudio
                        try { assertThat(result.edit.selection.start).isEqualTo(100800) }
                        finally { withContext(Dispatchers.IO) { result.file.delete() } }
                    } else {
                        withTimeoutOrNull(5000) { while (startFrame() < source.quietFrames - 12000) delay(1) }
                        assertThat(startFrame()).isAtLeast(source.quietFrames - 12000)
                        assertThat(startFrame()).isLessThan(source.quietFrames)
                        controller.release()
                        assertThat(withTimeout(5000) { pending.await() }).isEqualTo(AudioEditorResult.Cancelled)
                    }
                } finally { controller.release() }
            }
        } finally {
            recorder.close()
            withContext(Dispatchers.Main) { popup.release() }
        }
    }

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
            settings.setMinSilenceMillis(2500)
            settings.setSilenceMarginMillis(0)
            assertThat(recorder.start(SilentSource(tone = true))).isTrue()
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
                var copies = 0
                var preparedFile: java.io.File? = null
                var providerFailure: Throwable? = null
                val pending = async { controller.edit(scan, "", "", prepareAudio = { file ->
                    copies++
                    if (preparedFile == null) preparedFile = file else assertThat(file).isEqualTo(preparedFile)
                    val uri = try {
                        FileProvider.getUriForFile(context, context.packageName + AnkiDroid.AUTHORITY_SUFFIX, file)
                    } catch (failure: Exception) { providerFailure = failure; throw failure }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri).use { stream ->
                            assertThat(checkNotNull(stream).read()).isAtLeast(0)
                        }
                    }
                    copies > 1
                }) }
                ready()
                assertThat(evaluate("AudioEditor.state.minPauseSeconds")).isEqualTo("2.5")
                assertThat(evaluate("AudioEditor.state.edgeMarginMillis")).isEqualTo("0")
                withTimeout(5000) {
                    while (evaluate("!!AudioEditor.state.detailSummary && AudioEditor.state.overviewSummary.length===200") != "true") delay(1)
                }
                val shownEnd = evaluate("AudioEditor.state.sourceEnd").toLong()
                withTimeout(5000) {
                    while (evaluate("AudioEditor.state.newFrames>0") != "true") delay(1)
                }
                assertThat(evaluate("AudioEditor.state.sourceEnd").toLong()).isEqualTo(shownEnd)
                assertThat(evaluate("AudioEditor.state.overviewSummary.some(v=>v>0.1)")).isEqualTo("true")
                evaluate("document.getElementById('sl-action-play').click(); true")
                withTimeout(5000) { while (!recorder.isPreviewPlaying) delay(1) }
                evaluate("document.getElementById('sl-mode-original').click(); true")
                assertThat(evaluate("AudioEditor.state.mode")).isEqualTo("\"original\"")
                evaluate("document.getElementById('sl-mode-result').click(); true")
                evaluate("document.getElementById('sl-action-play').click(); true")
                withTimeout(5000) { while (recorder.isPreviewPlaying) delay(1) }
                assertThat(evaluate("AudioEditor.state.phase")).isEqualTo("\"editing\"")
                val initialEnd = evaluate("AudioEditor.state.b").toLong()
                assertThat(initialEnd).isGreaterThan(100L)
                evaluate("AudioEditor.state.b = Math.floor(AudioEditor.state.b / 2); true")
                assertThat(controller.requestSave()).isTrue()
                withTimeout(5000) { while (evaluate("AudioEditor.state.phase") != "\"failed\"") delay(1) }
                assertThat(pending.isCompleted).isFalse()
                assertThat(providerFailure).isNull()
                assertThat(checkNotNull(preparedFile).exists()).isTrue()
                assertThat(evaluate("AudioEditor.state.b").toLong()).isEqualTo(initialEnd / 2)
                assertThat(controller.requestSave()).isTrue()
                result = withTimeout(10_000) { pending.await() } as AudioEditorResult.WithAudio
                assertThat(copies).isEqualTo(2)
                assertThat(result!!.edit.selection.endExclusive).isEqualTo(initialEnd / 2)
                assertThat(result!!.file.length()).isGreaterThan(0L)
                assertThat(evaluate("AudioEditor.state.isOpen")).isEqualTo("false")

                // Export alone must not promote the remembered selection.
                var abandonedFile: java.io.File? = null
                val abandoned = async { controller.edit(scan, "", "", prepareAudio = { abandonedFile = it; false }) }
                ready()
                assertThat(evaluate("AudioEditor.state.b").toLong()).isEqualTo(initialEnd)
                controller.requestSave()
                withTimeout(5000) { while (evaluate("AudioEditor.state.phase") != "\"failed\"") delay(1) }
                evaluate("document.getElementById('sl-action-without').click(); true")
                assertThat(withTimeout(5000) { abandoned.await() }).isEqualTo(AudioEditorResult.WithoutAudio)
                assertThat(checkNotNull(abandonedFile).exists()).isFalse()

                controller.confirm(result!!)
                var cancelledFile: java.io.File? = null
                val confirmed = async { controller.edit(scan, "", "", prepareAudio = { cancelledFile = it; false }) }
                ready()
                assertThat(evaluate("AudioEditor.state.b").toLong()).isEqualTo(initialEnd / 2)
                controller.requestSave()
                withTimeout(5000) { while (evaluate("AudioEditor.state.phase") != "\"failed\"") delay(1) }
                page.container.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                page.container.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                assertThat(withTimeout(5000) { confirmed.await() }).isEqualTo(AudioEditorResult.Cancelled)
                assertThat(checkNotNull(cancelledFile).exists()).isFalse()

                val stopping = async { controller.edit(scan, "", "") }
                ready()
                val lease = checkNotNull(recorder.retainContext(scan))
                try {
                    recorder.stop()
                    controller.releaseContext(scan, cancelActive = false)
                    recorder.releaseContext(scan)
                    assertThat(recorder.start(SilentSource())).isTrue()
                    assertThat(recorder.isRecording(scan)).isFalse()
                    assertThat(stopping.isCompleted).isFalse()
                    assertThat(controller.requestSave()).isTrue()
                    val retained = withTimeout(10_000) { stopping.await() } as AudioEditorResult.WithAudio
                    try {
                        assertThat(retained.contextId).isEqualTo(scan)
                        assertThat(retained.edit).isEqualTo(result!!.edit)
                        assertThat(retained.file.length()).isGreaterThan(0)
                    } finally { withContext(Dispatchers.IO) { retained.file.delete() } }
                } finally { lease.close() }
                val fresh = checkNotNull(recorder.acquireScan())
                val releasing = async { controller.edit(fresh, "", "") }
                ready()
                controller.release()
                page.destroy()
                assertThat(withTimeout(5000) { releasing.await() }).isEqualTo(AudioEditorResult.Cancelled)
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

    private class QuietThenSpeechSource : AudioSource {
        override val sampleRate = 48000
        override val channelCount = 1
        val quietFrames = sampleRate * 7L
        val speech = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        private val stopping = AtomicBoolean()
        private var frame = 0L
        private var origin = 0L
        override fun start(): Boolean { origin = SystemClock.elapsedRealtimeNanos(); return true }
        override fun timestampNanos() = origin
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (frame == quietFrames) speech.await()
            if (frame == quietFrames + sampleRate) stopped.await()
            if (stopping.get()) return -1
            Thread.sleep(5)
            val end = if (frame < quietFrames) quietFrames else quietFrames + sampleRate
            val frames = minOf(length / 2L, end - frame).toInt()
            for (i in 0 until frames) {
                val value = if (frame < quietFrames) 0 else (sin(2 * PI * 440 * frame / sampleRate) * 10000).toInt()
                buffer[offset + i * 2] = value.toByte()
                buffer[offset + i * 2 + 1] = (value shr 8).toByte()
                frame++
            }
            return frames * 2
        }
        override fun stop() { stopping.set(true); speech.countDown(); stopped.countDown() }
        override fun close() = stop()
    }

    private class SilentSource(private val tone: Boolean = false) : AudioSource {
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
            val firstFrame = (sampleRate * 4 - remaining) / 2
            for (i in 0 until count / 2) {
                val value = if (tone) (sin(2 * PI * 440 * (firstFrame + i) / sampleRate) * 10000).toInt() else 0
                buffer[offset + i * 2] = value.toByte()
                buffer[offset + i * 2 + 1] = (value shr 8).toByte()
            }
            remaining -= count
            return count
        }
        override fun stop() { stopped.countDown() }
        override fun close() { stopped.countDown() }
    }
}
