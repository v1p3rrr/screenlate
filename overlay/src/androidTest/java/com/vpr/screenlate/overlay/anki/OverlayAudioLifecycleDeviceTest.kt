package com.vpr.screenlate.overlay.anki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiBackend
import com.vpr.screenlate.core.anki.AnkiDeck
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiModel
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.ExistingNote
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.core.translate.SentenceTranslator
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryDao
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Lifecycle, screen-off / outside-tap protection (B1), and non-blocking open latency (2.1) for PopupNotes + AudioEditor. */
@RunWith(AndroidJUnit4::class)
class OverlayAudioLifecycleDeviceTest {

    @Test
    fun outsideTapAndScreenOffDoNotDismissActiveEditorAndForceCloseCancelsBoth() = runBlocking {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val spy = ScreenOffSpyContext(baseContext)
        val views = mutableListOf<View>()
        val viewParams = mutableMapOf<View, WindowManager.LayoutParams>()
        val realWm = baseContext.getSystemService(WindowManager::class.java)
        val proxyWm = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, m, args ->
            when (m.name) {
                "addView" -> {
                    val v = args[0] as View
                    val p = WindowManager.LayoutParams().apply { copyFrom(args[1] as WindowManager.LayoutParams) }
                    views += v
                    viewParams[v] = p
                    null
                }
                "updateViewLayout" -> {
                    val v = args[0] as View
                    viewParams[v] = WindowManager.LayoutParams().apply { copyFrom(args[1] as WindowManager.LayoutParams) }
                    null
                }
                "removeView", "removeViewImmediate" -> {
                    val v = args[0] as View
                    views -= v
                    viewParams.remove(v)
                    null
                }
                else -> m.invoke(realWm, *(args ?: emptyArray()))
            }
        } as WindowManager

        val closeCalls = AtomicInteger(0)
        val callbacks = object : LookupPage.Callbacks {
            override fun onClose() { closeCalls.incrementAndGet() }
            override fun onLookup(query: String, primaryReading: String?) = Unit
            override fun onOpenUrl(url: String) = Unit
            override fun onKanji(character: String) = Unit
            override fun onCopy(text: String, html: String?) = Unit
            override fun media(dictionary: String, path: String): ByteArray? = null
        }

        val popup = withContext(Dispatchers.Main) { PopupController(spy, proxyWm, callbacks) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val recorder = SentenceRecorder(baseContext, RecordingSettingsRepository(Store()), PlaybackCoordinator())
        var audioController: AudioEditorController? = null
        try {
            assertThat(recorder.start(SilentSource())).isTrue()
            delay(150)
            val scanId = checkNotNull(recorder.acquireScan())

            withContext(Dispatchers.Main) {
                popup.hideOnOutsideTap = true
                popup.attach()
                popup.show(
                    PageState.build(baseContext, PageTheme.DARK, "", 0, emptyList(), "-", language = Language.JAPANESE),
                    Box(100f, 100f, 200f, 200f),
                    null,
                    Box(0f, 0f, 1080f, 1920f),
                    900f,
                )
            }
            val outsideCatcher = withContext(Dispatchers.Main) { views.first { it !== popup.page.container } }
            withTimeout(5_000) {
                while (withContext(Dispatchers.Main) {
                    viewParams[outsideCatcher]!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } != 0) {
                    delay(10)
                }
            }
            assertThat(spy.registeredReceiver).isNotNull()

            val controller = withContext(Dispatchers.Main) {
                AudioEditorController(baseContext, popup.page, popup, recorder, RecordingSettingsRepository(Store()))
            }.also { audioController = it }
            val ready = CompletableDeferred<Unit>()
            // Hook page.audioEditorActions set by controller to know when ready arrives.
            val delegate = popup.page.audioEditorActions
            popup.page.audioEditorActions = object : LookupPage.AudioEditorActions {
                override fun onAudioEditorCommand(payload: String) {
                    delegate?.onAudioEditorCommand(payload)
                    if (JSONObject(payload).optString("action") == "ready") ready.complete(Unit)
                }
            }

            val notes = buildPopupNotes(
                context = spy,
                scope = scope,
                page = popup.page,
                ankiBackend = FakeAnkiBackend(),
                audioEditor = controller,
                recorder = recorder,
                scanContext = scanId,
                httpDelayMs = 0L,
            )

            withContext(Dispatchers.Main) {
                popup.page.noteActions = notes
                notes.onPrepareNote(
                    request = "force-close-op",
                    index = 0,
                    noteData = """{"values":{"expression":"猫"},"term":{"expression":"猫","reading":"ねこ"}}""",
                )
                notes.onCommitPreparedNote(
                    request = "force-close-op",
                    withScreenshot = false,
                    force = false,
                    audio = 1,
                )
            }
            withTimeout(5_000) { ready.await() }
            withTimeout(5_000) { while (!controller.isActive) delay(10) }

            withContext(Dispatchers.Main) {
                assertThat(popup.isAudioEditorActive).isTrue()
                assertThat(outsideCatcher.visibility).isEqualTo(View.GONE)
                assertThat(viewParams[outsideCatcher]!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    .isNotEqualTo(0)
                assertThat(spy.unregisterCount).isGreaterThan(0)

                val down = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 5f, 5f, 0)
                val up = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 5f, 5f, 0)
                outsideCatcher.dispatchTouchEvent(down)
                outsideCatcher.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
                spy.registeredReceiver?.onReceive(spy, Intent(Intent.ACTION_SCREEN_OFF))

                assertThat(closeCalls.get()).isEqualTo(0)
                assertThat(popup.isShowing).isTrue()
                assertThat(popup.isAudioEditorActive).isTrue()
                assertThat(controller.isActive).isTrue()

                notes.onPopupForceClosed()
                popup.hide()
            }

            withTimeout(5_000) { while (controller.isActive || notes.retainsContext) delay(10) }
            assertThat(controller.isActive).isFalse()
            assertThat(notes.retainsContext).isFalse()
            assertThat(popup.isAudioEditorActive).isFalse()
        } finally {
            withContext(Dispatchers.Main) {
                audioController?.release()
                popup.release()
            }
            scope.cancel()
            recorder.close()
        }
    }

    @Test
    fun slowNetworkTranslationDoesNotBlockOpeningAudioEditor() = runBlocking {
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

        val popup = withContext(Dispatchers.Main) { PopupController(context, proxyWm, callbacks) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val recorder = SentenceRecorder(context, RecordingSettingsRepository(Store()), PlaybackCoordinator())
        var audioController: AudioEditorController? = null
        try {
            assertThat(recorder.start(SilentSource())).isTrue()
            delay(150)
            val scanId = checkNotNull(recorder.acquireScan())

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
            val controller = withContext(Dispatchers.Main) {
                AudioEditorController(context, popup.page, popup, recorder, RecordingSettingsRepository(Store()))
            }.also { audioController = it }
            val openedReady = CompletableDeferred<Unit>()
            val delegate = popup.page.audioEditorActions
            popup.page.audioEditorActions = object : LookupPage.AudioEditorActions {
                override fun onAudioEditorCommand(payload: String) {
                    delegate?.onAudioEditorCommand(payload)
                    if (JSONObject(payload).optString("action") == "ready") openedReady.complete(Unit)
                }
            }

            val httpCalls = AtomicInteger(0)
            val notes = buildPopupNotes(
                context = context,
                scope = scope,
                page = popup.page,
                ankiBackend = FakeAnkiBackend(),
                audioEditor = controller,
                recorder = recorder,
                scanContext = scanId,
                httpDelayMs = 3_000L,
                httpCalls = httpCalls,
            )

            val startedMs = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) {
                popup.page.noteActions = notes
                notes.onPrepareNote(
                    request = "latency-op",
                    index = 0,
                    noteData = """{"values":{"expression":"食べる"},"term":{"expression":"食べる","reading":"たべる"}}""",
                )
                notes.onCommitPreparedNote(
                    request = "latency-op",
                    withScreenshot = false,
                    force = false,
                    audio = 1,
                )
            }
            withTimeout(5_000) { openedReady.await() }
            val elapsedMs = SystemClock.elapsedRealtime() - startedMs
            assertThat(elapsedMs).isLessThan(1_800L)
            withTimeout(2_000) { while (httpCalls.get() == 0) delay(10) }
            assertThat(httpCalls.get()).isGreaterThan(0)

            withContext(Dispatchers.Main) { notes.onPopupForceClosed() }
            withTimeout(5_000) { while (controller.isActive || notes.retainsContext) delay(10) }
        } finally {
            withContext(Dispatchers.Main) {
                audioController?.release()
                popup.release()
            }
            scope.cancel()
            recorder.close()
        }
    }

    private suspend fun buildPopupNotes(
        context: Context,
        scope: CoroutineScope,
        page: LookupPage,
        ankiBackend: AnkiBackend,
        audioEditor: AudioEditorController,
        recorder: SentenceRecorder,
        scanContext: com.vpr.screenlate.core.media.AudioContextId,
        httpDelayMs: Long,
        httpCalls: AtomicInteger = AtomicInteger(0),
    ): PopupNotes {
        val ankiSettingsRepo = AnkiSettingsRepository(Store()).apply {
            update(Language.JAPANESE) {
                AnkiSettings(
                    deckId = 1L,
                    deckName = "Default",
                    modelId = 10L,
                    modelName = "Basic",
                    fields = mapOf("Front" to "{expression} {sentence-translation} {sentence-audio}"),
                    duplicateCheck = false,
                )
            }
        }
        val ankiNotes = AnkiNotes(ankiBackend, ankiSettingsRepo)
        val audioSettings = AudioSettingsRepository(Store())
        val appSettings = AppSettingsRepository(Store())
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            httpCalls.incrementAndGet()
            if (httpDelayMs > 0) Thread.sleep(httpDelayMs)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""[{"translations":[{"text":"to eat"}]}]""".toResponseBody())
                .build()
        }.build()
        val translationSettings = TranslationSettingsRepository(Store())
        val translator = SentenceTranslator(context, http, translationSettings)
        val sentenceTranslation = SentenceTranslation(translator, translationSettings, appSettings)
        val dictRepo = DictionaryRepository(
            EmptyDao,
            EmptyEngine,
            DictionaryStorage(context),
            Store(),
            DictionaryLanguageDetector(context),
        )
        val lookup = DictionaryLookup(dictRepo, EmptyEngine, LookupSettingsRepository(Store())) {
            error("unused imports")
        }
        return PopupNotes(
            context = context,
            scope = scope,
            page = page,
            anki = AnkiDroid(context),
            notes = ankiNotes,
            audio = AudioFinder(context, http, audioSettings, appSettings),
            audioSettings = audioSettings,
            player = AudioPlayer(context, audioSettings, PlaybackCoordinator()),
            lookup = lookup,
            language = { Language.JAPANESE },
            isShowing = { true },
            noteSource = {
                NoteSource {
                    NoteContext(
                        sentence = com.vpr.screenlate.core.anki.note.Sentence("", "食べる", ""),
                        image = null,
                        audioContextId = scanContext,
                    )
                }
            },
            translation = sentenceTranslation,
            cropEditor = null,
            audioEditor = audioEditor,
            recordingSettings = RecordingSettingsRepository(Store()),
            currentAudioContext = { scanContext },
            onAnkiOpened = {},
            onNoteAdded = {},
            onOpenAnkiSettings = {},
        )
    }

    private class ScreenOffSpyContext(base: Context) : ContextWrapper(base) {
        var registeredReceiver: BroadcastReceiver? = null
        var unregisterCount = 0

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            if (filter?.hasAction(Intent.ACTION_SCREEN_OFF) == true) registeredReceiver = receiver
            return super.registerReceiver(receiver, filter)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            flags: Int,
        ): Intent? {
            if (filter?.hasAction(Intent.ACTION_SCREEN_OFF) == true) registeredReceiver = receiver
            return super.registerReceiver(receiver, filter, flags)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
        ): Intent? {
            if (filter?.hasAction(Intent.ACTION_SCREEN_OFF) == true) registeredReceiver = receiver
            return super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int,
        ): Intent? {
            if (filter?.hasAction(Intent.ACTION_SCREEN_OFF) == true) registeredReceiver = receiver
            return if (Build.VERSION.SDK_INT >= 26) {
                super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
            } else {
                super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
            }
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            if (receiver === registeredReceiver) unregisterCount++
            super.unregisterReceiver(receiver)
        }
    }

    private class FakeAnkiBackend : AnkiBackend {
        override fun availability() = AnkiAvailability.READY
        override suspend fun decks() = listOf(AnkiDeck(1L, "Default"))
        override suspend fun models() = listOf(AnkiModel(10L, "Basic"))
        override suspend fun fields(modelId: Long) = listOf("Front")
        override suspend fun addNote(modelId: Long, deckId: Long, fields: List<String>, tags: Set<String>) = 100L
        override suspend fun updateNote(noteId: Long, fields: List<String>, tags: Set<String>) = true
        override suspend fun findDuplicates(firstField: String, modelIds: List<Long>, scope: DuplicateScope, deckId: Long): List<ExistingNote> = emptyList()
        override suspend fun addMedia(file: File, preferredName: String, kind: AnkiDroid.MediaKind) = "[sound:${file.name}]"
    }

    private object EmptyDao : DictionaryDao {
        override fun observeAll(): Flow<List<DictionaryEntity>> = flowOf(emptyList())
        override suspend fun getAll(): List<DictionaryEntity> = emptyList()
        override suspend fun get(id: Long): DictionaryEntity? = null
        override suspend fun findByTitle(title: String): DictionaryEntity? = null
        override suspend fun maxPriority(): Int = -1
        override suspend fun insert(dictionary: DictionaryEntity): Long = 0L
        override suspend fun update(dictionary: DictionaryEntity) = Unit
        override suspend fun update(dictionaries: List<DictionaryEntity>) = Unit
        override suspend fun delete(dictionary: DictionaryEntity) = Unit
    }

    private object EmptyEngine : DictionaryEngine {
        override suspend fun import(archive: File, outputDir: File, formsOnly: Boolean): ImportedDictionary =
            error("unused")
        override suspend fun load(dictionaries: DictionarySet) = Unit
        override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> = emptyList()
        override suspend fun styles(): List<DictionaryStyle> = emptyList()
        override suspend fun media(dictionary: String, path: String): ByteArray? = null
        override suspend fun kanji(character: String): KanjiResult = KanjiResult("", emptyList())
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
}
