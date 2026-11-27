package com.vpr.screenlate.overlay.anki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.graphics.scale
import com.vpr.screenlate.core.anki.AddResult
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.NotePreparation
import com.vpr.screenlate.core.anki.NoteRequest
import com.vpr.screenlate.core.anki.NotePreparationResult
import com.vpr.screenlate.core.anki.SentenceAudioAttachment
import com.vpr.screenlate.core.anki.audio.AudioCandidate
import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.Pronunciation
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.anki.label
import com.vpr.screenlate.core.anki.message
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.anki.settings.CropAreaRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.media.AudioContextId
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.core.common.language.AudioRegion
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.overlay.ui.CropEditor
import com.vpr.screenlate.overlay.web.LookupPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import com.vpr.screenlate.core.common.redacted

/**
 * Where the looked-up word came from: its sentence and an owned source image.
 *
 * @property image pinned for the note, which closes it when done.
 * @property focus the word's paragraph in source-image coordinates, the initial crop frame.
 * @property documentTitle name of the app the word was found in, or the word "Camera" for a photo, for `{document-title}`.
 */
data class NoteContext(
    val sentence: Sentence?,
    val image: NoteImage?,
    val focus: Box? = null,
    val documentTitle: String = "",
    val audioContextId: AudioContextId? = null,
)

/** The entry of [term] among the shown [terms]: [index] while it is still there, else where it moved; null when gone. */
internal fun entryOf(terms: List<Pair<String, String>>, index: Int, term: Pair<String, String>): Int? =
    if (terms.getOrNull(index) == term) index else terms.indexOf(term).takeIf { it >= 0 }

/**
 * The extension for the copy of a dictionary media file handed to AnkiDroid: the file name's own (a dot in a folder
 * name does not count), png when there is none.
 */
internal fun mediaExtension(path: String): String =
    path.substringAfterLast('/').substringAfterLast('.', "")
        .takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
        ?.lowercase()
        ?: "png"

/** The longest side a WebP image may have. */
internal const val WEBP_MAX_SIDE = 16383

/** [width] × [height] scaled down in proportion until neither side exceeds [WEBP_MAX_SIDE]; unchanged when it fits. */
internal fun webpSize(width: Int, height: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= WEBP_MAX_SIDE) return width to height
    fun scaled(side: Int) = (side.toLong() * WEBP_MAX_SIDE / longest).toInt().coerceAtLeast(1)
    return scaled(width) to scaled(height)
}

/** Where a word came from, taken when its ➕ is pressed, so a view shown later does not change the note. */
fun interface NoteSource {
    /** The provider keeps the captured source identity; [screenshot] lazily asks for its picture after choice. */
    suspend fun context(screenshot: Boolean): NoteContext
    /** Reserve the original audio session synchronously with Prepare, before waiting for attachment choice. */
    fun retainAudio(): SentenceRecorder.ContextLease? = null
}

/**
 * The ➕/📖 and 🔊 buttons of a [LookupPage]: duplicate marks, adding notes to AnkiDroid, opening added notes,
 * playing and auto-playing audio, and the clip menu.
 *
 * Words added during one scan remember their notes until [onClosed], so their button opens the note instead of
 * adding it again.
 *
 * @param isShowing whether the lookup view is visible for audio playback and feedback.
 * @param noteSource called when ➕ is pressed; describes where the shown word came from.
 * @param translation fills `{sentence-translation}`.
 * @param onAnkiOpened called after a note was opened in AnkiDroid, e.g. to dock the bubble.
 * @param onNoteAdded called after ➕ added or updated a note for the word the popup still shows.
 * @param onOpenAnkiSettings shows Screenlate's Anki settings, where a broken setup is explained.
 */
class PopupNotes(
    private val context: Context,
    private val scope: CoroutineScope,
    private val page: LookupPage,
    private val anki: AnkiDroid,
    private val notes: AnkiNotes,
    private val audio: AudioFinder,
    private val audioSettings: AudioSettingsRepository,
    private val player: AudioPlayer,
    private val lookup: DictionaryLookup,
    /** The open scan's language. */
    private val language: () -> Language,
    isShowing: () -> Boolean,
    private val noteSource: () -> NoteSource,
    private val translation: SentenceTranslation,
    private val cropEditor: CropEditor?,
    private val cropAreaSettings: CropAreaRepository? = null,
    private val audioEditor: AudioEditorController? = null,
    private val recordingSettings: RecordingSettingsRepository? = null,
    private val currentAudioContext: () -> AudioContextId? = { null },
    private val onAnkiOpened: () -> Unit,
    private val onNoteAdded: () -> Unit,
    private val onOpenAnkiSettings: () -> Unit,
) : LookupPage.NoteActions {
    private val json = Json { ignoreUnknownKeys = true }
    private var duplicateJob: Job? = null
    private val statusCache = AnkiStatusCache(ANKI_STATUS_TTL_MS)
    private var lastAutoPlayed: Pair<String, String>? = null
    private var autoPlayJob: Job? = null
    private var pendingAutoPlay: Pair<String, String>? = null
    private val audioMenu = PopupAudioMenu(scope, ::loadAudioMenu, page::showAudioMenu)
    private var audioToast: Toast? = null
    private val audioPlayback = PopupAudioPlayback(scope, isShowing, ::currentTerms, player::play, ::setAudioFailure)

    private val scanNotes = ScanNotes()

    /** Notes that the 📖 buttons of the current view open, by entry index. */
    private val openableNotes = mutableMapOf<Int, List<Long>>()

    /** `(expression, reading)` sequence last passed to [onResultsShown], to preserve [openableNotes] on refined redraws. */
    private var lastShownTerms: List<Pair<String, String>>? = null

    /** Entry indices currently being added/updated, so [markNotes] does not overwrite their `"busy"` button state. */
    private val addingEntries = mutableSetOf<Int>()
    private val pendingNote = PendingNoteController(scope)
    val retainsContext: Boolean get() = pendingNote.retainsContext
    internal val pendingToken: PendingNoteController.Token? get() = pendingNote.token
    val canFinish: Boolean get() = audioEditor?.canFinish == true || cropEditor?.canConfirm == true

    internal fun finishForScan(token: PendingNoteController.Token, scan: () -> Unit): Boolean {
        if (!pendingNote.finishThen(token, scan)) return false
        if (audioEditor?.requestSave() != true) cropEditor?.confirm()
        return true
    }

    internal fun cancelForScan(token: PendingNoteController.Token, scan: () -> Unit): Boolean {
        if (pendingNote.cancelThen(token, scan)) return true
        if (pendingNote.token !== token) return false
        pendingNote.finishThen(token, scan)
        toast(context.getString(R.string.audio_scan_write_in_progress))
        return true
    }

    internal fun keepEditing(token: PendingNoteController.Token) = pendingNote.keepEditing(token)

    /** Cancels an active attachment editor if the host popup closed unexpectedly (e.g. renderer crash). */
    fun onPopupForceClosed() {
        if (audioEditor?.isActive == true) {
            audioEditor.cancelActive()
            pendingNote.cancel()
        } else if (pendingNote.retainsContext) {
            cropEditor?.cancel()
            pendingNote.contextEnded()
        }
    }

    fun onActiveLanguageChanged(language: Language) {
        if (pendingNote.cancelForLanguage(language)) toast(context.getString(R.string.anki_operation_settings_changed))
    }

    /** The clip chosen for each term; the choice goes into `{audio}`. */
    private val chosenClips = mutableMapOf<Pair<String, String>, AudioClip>()
    private var lastNoteConfig: JsonObject? = null

    init {
        page.noteActions = this
    }

    /** Shows or hides the entry buttons according to the current settings. Call before showing results. */
    suspend fun refreshActions() {
        val status = ankiStatus()
        val ankiReady = status == AnkiStatus.Ready
        val audioEnabled = audioSettings.current(language()).sources.isNotEmpty()
        val problem = (status as? AnkiStatus.Broken)?.problem?.let { context.getString(it.message) }
        page.setActions(anki = ankiReady, audio = audioEnabled, ankiProblem = problem)
        // The frequency modes also order each dictionary's frequency values in the popup, with or without Anki, and the
        // audio regions order the transcriptions.
        val regions = AudioRegion.ordered(audioSettings.current(language()).regions, language().support.audioRegions)
        val ankiSettings = notes.settings(language())
        val config = buildJsonObject {
            put("markers", if (ankiReady) buildJsonArray { notes.usedMarkers(language()).forEach { add(JsonPrimitive(it)) } } else JsonNull)
            put("frequencyModes", buildJsonObject { lookup.frequencyModes().forEach { (title, mode) -> put(title, mode) } })
            put("regions", buildJsonArray {
                regions.forEach { region ->
                    add(buildJsonObject {
                        put("id", region.id)
                        put("codes", buildJsonArray { region.codes.forEach { add(JsonPrimitive(it)) } })
                    })
                }
            })
            put("screenshotByDefault", JsonPrimitive(ankiSettings.screenshotByDefault))
            val hasAudio = Build.VERSION.SDK_INT >= 29 && currentAudioContext() != null
            put("editorByDefault", JsonPrimitive(recordingSettings?.cachedSettings?.defaultEditor ?: false))
            put("sentenceAudioEligible", JsonPrimitive(ankiReady && FieldTemplate.SENTENCE_AUDIO in notes.usedMarkers(language()) && hasAudio))
            put("sortFrequencyMode", JsonPrimitive(lookup.sortFrequencyMode(language()).orEmpty()))
        }
        lastNoteConfig = config
        page.setNoteConfig(config)
    }

    /** The Anki setup is checked against AnkiDroid at most every few seconds, not for every word. */
    private suspend fun ankiStatus(): AnkiStatus = statusCache.get(language(), notes::status)

    /** Called after a view with results is shown: schedules the duplicate check and auto-play. */
    fun onResultsShown(results: List<LookupResult>, position: TextPosition? = null) {
        val terms = results.map { it.term.expression to it.term.reading }
        val firstTerm = terms.firstOrNull()
        audioPlayback.onResultsShown(results, position)
        if (terms != lastShownTerms) {
            openableNotes.clear()
            addingEntries.clear()
        }
        lastShownTerms = terms
        duplicateJob?.cancel()
        duplicateJob = scope.launch {
            delay(DUPLICATE_CHECK_DELAY_MS)
            markNotes()
        }
        // Auto-play waits until the aim rests on a word, so words passed on the way stay silent.
        autoPlayJob?.cancel()
        pendingAutoPlay = firstTerm?.takeIf { it != lastAutoPlayed }
        if (pendingAutoPlay != null) {
            autoPlayJob = scope.launch {
                delay(AUTO_PLAY_DELAY_MS)
                autoPlayNow()
            }
        }
    }

    /** The earlier view is back: its entries are marked again, without auto-play. */
    override fun onViewRestored() {
        onResultsHidden()
        lastShownTerms = null
        openableNotes.clear()
        duplicateJob = scope.launch { markNotes() }
    }

    /** The popup went away without closing the scan, e.g. the aim moved to text without results. */
    fun onResultsHidden() {
        duplicateJob?.cancel()
        cancelPendingAudio()
    }

    /** The dictionary may remain hosted while the audio editor replaces it. */
    fun cancelPendingAudio() {
        autoPlayJob?.cancel()
        audioMenu.clear()
        audioPlayback.onResultsHidden()
        pendingAutoPlay = null
    }

    /** The finger was lifted: a pending auto-play starts without waiting. */
    fun onAimSettled() {
        if (pendingAutoPlay == null) return
        autoPlayJob?.cancel()
        autoPlayJob = scope.launch { autoPlayNow() }
    }

    private suspend fun autoPlayNow() {
        val term = pendingAutoPlay ?: return
        pendingAutoPlay = null
        lastAutoPlayed = term
        if (audioSettings.current(language()).autoPlay) play(term.first, term.second)
    }

    /**
     * The scan ended (bubble docked, screen rotated or left): forget notes added and clips chosen during it. An open
     * crop editor closes as if cancelled, so its note is not added.
     */
    fun onClosed() {
        pendingNote.contextEnded()
        cropEditor?.cancel()
        onResultsHidden()
        statusCache.clear()
        lastAutoPlayed = null
        lastShownTerms = null
        addingEntries.clear()
        scanNotes.close()
        openableNotes.clear()
        chosenClips.clear()
    }

    /** Words added during this scan get 📖; duplicates get 📖 or a mark, depending on the duplicate behavior. */
    private suspend fun markNotes() {
        val settings = notes.settings(language())
        if (ankiStatus() != AnkiStatus.Ready) return
        val terms = currentTerms() ?: return
        val known = mutableMapOf<Int, String>()
        terms.forEachIndexed { index, term ->
            scanNotes[term]?.let { ids ->
                openableNotes[index] = ids
                known[index] = "added"
            }
        }
        if (known.isNotEmpty()) page.setNoteStates(known)
        if (!settings.duplicateCheck) {
            val cleared = terms.indices.filter { it !in known && it !in addingEntries }.associateWith { "" }
            if (cleared.isNotEmpty()) page.setNoteStates(cleared)
            return
        }
        val markers = notes.duplicateCheckMarkers(language())
        val entries = evaluateJson<List<Map<String, String>>>(
            "JSON.stringify(Popup.allNoteData(${Json.encodeToString(markers.toList())}))",
        ).orEmpty()
        entries.forEachIndexed { index, values ->
            if (index in known || index in addingEntries) return@forEachIndexed
            val ids = try {
                notes.duplicateIds(language(), values)
            } catch (e: CancellationException) {
                // The view changed: its marks must not reach the next one.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Duplicate check failed", e.redacted())
                emptyList()
            }
            if (index in addingEntries) return@forEachIndexed
            if (ids.isEmpty()) {
                openableNotes.remove(index)
                page.setNoteStates(mapOf(index to ""))
                return@forEachIndexed
            }
            openableNotes[index] = ids
            val state = when (settings.duplicateBehavior) {
                DuplicateBehavior.PREVENT -> "open"
                DuplicateBehavior.NEW -> "duplicate_new"
                DuplicateBehavior.OVERWRITE -> "duplicate_overwrite"
            }
            page.setNoteStates(mapOf(index to state))
        }
    }

    private suspend fun currentTerms(): List<Pair<String, String>>? =
        evaluateJson<List<List<String>>>("JSON.stringify(Popup.terms())")
            ?.map { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }

    /** evaluateJavascript returns a string result JSON-encoded once more. */
    private suspend inline fun <reified T> evaluateJson(script: String): T? {
        val raw = page.evaluate(script) ?: return null
        return runCatching { json.decodeFromString<T>(json.decodeFromString<String>(raw)) }.getOrNull()
    }

    override fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean, force: Boolean, withAudio: Boolean?) {
        val request = java.util.UUID.randomUUID().toString()
        onPrepareNote(request, index, noteData)
        pendingNote.choose(request, PendingNoteController.Attachments(withScreenshot, force, withAudio))
    }

    override fun onCommitPreparedNote(request: String, withScreenshot: Boolean, force: Boolean, audio: Int) {
        if (audio !in -1..1) return
        pendingNote.choose(request, PendingNoteController.Attachments(withScreenshot, force, when (audio) {
            0 -> false
            1 -> true
            else -> null
        }))
    }

    override fun onDismissPreparedNote(request: String) { pendingNote.dismiss(request) }

    override fun onPrepareNote(request: String, index: Int, noteData: String) {
        if (request.length > 100) return
        if (pendingNote.isActive) { page.cancelNotePreparation(request); onNoteWaiting(); return }
        val defaultAudio = recordingSettings?.cachedSettings?.defaultEditor ?: true
        val source = noteSource()
        val scan = scanNotes.scan
        // The note keeps the language it was asked for, even if the scan closes before it is added.
        val noteLanguage = language()
        val targetAtPress = notes.cachedTarget(noteLanguage)
        val audioLease = source.retainAudio()
        addingEntries += index
        pendingNote.prepare(request, noteLanguage, onRetired = {
            try { audioLease?.close() } finally {
                addingEntries -= index
                if (page.noteActions === this@PopupNotes) page.cancelNotePreparation(request)
            }
        }) {
            var image: NoteImage? = null
            var picture: Bitmap? = null
            var configurationWatch: Job? = null
            var confirmedAudio: AudioEditorResult.WithAudio? = null
            var sentenceAudio: SentenceAudioAttachment = SentenceAudioAttachment.None
            var term: Pair<String, String>? = null
            var opens: List<Long>? = null
            var screenshot: File? = null
            var clip: AudioClip? = null
            var translated: Deferred<String?>? = null
            var screenshotTask: BackgroundAttachment<File>? = null
            val createdScreenshots = java.util.Collections.synchronizedList(mutableListOf<File>())
            var clipTask: BackgroundAttachment<AudioClip?>? = null
            var furiganaTask: BackgroundAttachment<Pair<String, String>?>? = null
            val state = try {
                val data = json.decodeFromString<NoteDataDto>(noteData)
                val noteTerm = data.term.expression to data.term.reading
                term = noteTerm
                val target = targetAtPress ?: notes.freezeTarget(noteLanguage)
                val operation = this
                configurationWatch = launch {
                    notes.targetChanged(target).first { it }
                    toast(context.getString(R.string.anki_operation_settings_changed))
                    operation.cancel()
                }
                val used = target.markers
                val frozen = source.context(false)
                requireContext()
                if (scan != scanNotes.scan) return@prepare
                val attachments = attachments()
                val effectiveWithAudio = attachments.audio ?: defaultAudio
                val force = attachments.force
                val context = if (attachments.screenshot && "screenshot" in used) source.context(true) else frozen
                image = context.image
                if (!effectiveWithAudio || FieldTemplate.SENTENCE_AUDIO !in used || context.audioContextId == null) {
                    audioLease?.close()
                    attachmentsReady()
                }
                // Asked now, so it comes while the crop editor is open; the wait counts from when the note is ready.
                val sentence = context.sentence?.text
                if (FieldTemplate.SENTENCE_TRANSLATION in used && sentence != null) {
                    translated = async { translation.forNote(sentence, noteLanguage) }
                }
                // The editor opens first, while the scan it shows is still on the screen.
                if (attachments.screenshot && context.image != null && cropEditor != null) {
                    val rememberControl = if (context.image is ScreenNoteImage && cropAreaSettings != null) {
                        val imageBounds = context.image.bounds
                        val cropSettings = cropAreaSettings.current()
                        // The scan may have closed while reading preferences; do not open an editor afterwards.
                        if (scan != scanNotes.scan) return@prepare
                        CropEditor.RememberControl(
                            initial = cropSettings.rememberArea,
                            remembered = cropSettings.cropFor(imageBounds),
                            updates = cropAreaSettings.settings.map { it.rememberArea }.distinctUntilChanged(),
                            onChange = cropAreaSettings::setRememberArea,
                            onConfirm = { selection -> cropAreaSettings.saveCrop(selection, imageBounds) },
                        )
                    } else {
                        null
                    }
                    // Cancelling the editor cancels the note.
                    picture = cropEditor.edit(context.image, context.focus, rememberControl)
                        ?: run {
                            showState(scan, index, noteTerm, "", null)
                            return@prepare
                        }
                }
                // The crop is a bitmap of its own.
                image?.close()
                image = null
                val values = data.values.toMutableMap()
                context.sentence?.let { s ->
                    values["sentence"] = escapeHtml(s.text)
                    values["cloze-prefix"] = escapeHtml(s.prefix)
                    values["cloze-body"] = escapeHtml(s.body)
                    values["cloze-suffix"] = escapeHtml(s.suffix)
                }
                values["document-title"] = escapeHtml(context.documentTitle)
                // ClipboardManager must be obtained on the main thread (Android 7-8 creates a Handler inside it).
                val clipboardData = if ("clipboard-text" in used || "clipboard-image" in used) currentClip() else null
                if ("clipboard-text" in used) {
                    readClipboardText(clipboardData)?.let { values["clipboard-text"] = escapeHtml(it).replace("\n", "<br>") }
                }
                for ((marker, tag) in noteLanguage.support.scriptMarkers) {
                    if (marker !in used) continue
                    values[marker] = escapeHtml(lookup.inScript(data.term.expression, data.term.reading, tag, noteLanguage))
                }
                screenshotTask = picture?.let { bmp ->
                    BackgroundAttachment(this, Dispatchers.Default) { saveScreenshot(bmp, createdScreenshots::add) }
                }
                clipTask = if ("audio" in used) {
                    BackgroundAttachment(this) {
                        chosenClips[noteTerm] ?: audio.find(noteTerm.first, noteTerm.second, noteLanguage)
                    }
                } else {
                    null
                }
                val furiganaSentence = context.sentence?.text?.takeIf {
                    "sentence-furigana" in used || "sentence-furigana-plain" in used
                }
                furiganaTask = furiganaSentence?.let { s ->
                    BackgroundAttachment(this) { sentenceFurigana(s, noteLanguage) }
                }
                val prepMutex = Mutex()
                var ankiMediaImported = false
                var cachedPreparation: NotePreparation? = null
                suspend fun ensurePreparation(): NotePreparation {
                    cachedPreparation?.let { return it }
                    return prepMutex.withLock {
                        cachedPreparation?.let { return@withLock it }
                        furiganaTask?.await()?.let { (html, plain) ->
                            values["sentence-furigana"] = html
                            values["sentence-furigana-plain"] = plain
                        }
                        if (!ankiMediaImported) {
                            if ("clipboard-image" in used) {
                                readClipboardImage(clipboardData)?.let { values["clipboard-image"] = it }
                            }
                            if (data.media.isNotEmpty()) {
                                resolveGlossaryMedia(data.media, values, used, noteLanguage)
                            }
                            ankiMediaImported = true
                        }
                        translated?.let { withTimeoutOrNull(SentenceTranslation.NOTE_WAIT_MS) { it.await() } }
                            ?.let { values[FieldTemplate.SENTENCE_TRANSLATION] = escapeHtml(it) }
                        screenshot = screenshotTask?.await()
                        clip = clipTask?.await()
                        notes.preparation(target, NoteRequest(values, screenshot, clip), force).also {
                            cachedPreparation = it
                        }
                    }
                }
                var prepared: NotePreparationResult? = null
                if (effectiveWithAudio && FieldTemplate.SENTENCE_AUDIO in used && context.audioContextId != null && audioEditor != null) {
                    val firstMarkers = if (!force) notes.overwriteDuplicateCheckMarkers(target) else null
                    val isDuplicateKeep = if (firstMarkers != null) {
                        val needsFurigana = "sentence-furigana" in firstMarkers || "sentence-furigana-plain" in firstMarkers
                        val needsTranslation = FieldTemplate.SENTENCE_TRANSLATION in firstMarkers
                        val checkValues = if (needsFurigana || needsTranslation) {
                            values.toMutableMap().also { copy ->
                                if (needsFurigana) {
                                    runCatchingNonCancel { furiganaTask?.await() }.getOrNull()?.let { (html, plain) ->
                                        copy["sentence-furigana"] = html
                                        copy["sentence-furigana-plain"] = plain
                                    }
                                }
                                if (needsTranslation) {
                                    translated?.let { withTimeoutOrNull(SentenceTranslation.NOTE_WAIT_MS) { it.await() } }
                                        ?.let { copy[FieldTemplate.SENTENCE_TRANSLATION] = escapeHtml(it) }
                                }
                            }
                        } else {
                            values
                        }
                        notes.shouldSkipSentenceAudioForDuplicate(target, checkValues)
                    } else {
                        false
                    }
                    when (val audioResult = audioEditor.edit(context.audioContextId, noteTerm.first, noteTerm.second,
                        duplicateKeep = isDuplicateKeep, prepareAudio = { file ->
                            // Track staging before any copy/return suspension can cancel delivery of the editor result.
                            sentenceAudio = SentenceAudioAttachment.Attached(file)
                            val preparation = try {
                                ensurePreparation()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Preparing note attachments failed", e.redacted())
                                return@edit false
                            }
                            val result = preparation.prepare(SentenceAudioAttachment.Attached(file))
                            if (result is NotePreparationResult.MediaFailed) false else {
                                prepared = result
                                true
                            }
                        })) {
                        is AudioEditorResult.Cancelled -> {
                            showState(scan, index, noteTerm, "", null)
                            return@prepare
                        }
                        is AudioEditorResult.WithoutAudio -> {
                            prepared = null
                            sentenceAudio = SentenceAudioAttachment.Omitted
                            audioLease?.close()
                            attachmentsReady()
                        }
                        is AudioEditorResult.WithAudio -> {
                            confirmedAudio = audioResult
                            sentenceAudio = SentenceAudioAttachment.Attached(audioResult.file)
                            audioLease?.close()
                            attachmentsReady()
                        }
                    }
                }
                // Once a mutation is attempted, keep its actual result rather than cancelling its acknowledgement.
                audioLease?.close()
                attachmentsReady()
                val ready = prepared ?: ensurePreparation().prepare(sentenceAudio)
                val result = when (ready) {
                    is NotePreparationResult.Ready -> {
                        val written = notes.write(ready.note,
                            onWriteAttempt = { configurationWatch.cancel(); beginWrite() })
                        if (written is AddResult.Updated && ready.note.keepsSentenceAudioFields) {
                            toast(this@PopupNotes.context.getString(R.string.anki_sentence_fields_preserved))
                        }
                        written
                    }
                    is NotePreparationResult.Stopped -> ready.result
                    is NotePreparationResult.MediaFailed -> AddResult.Failed("Failed to transfer note media to Anki")
                }
                // The kind of result only: messages and fields may carry the note's text.
                Log.i(
                    TAG,
                    "Note: ${result.javaClass.simpleName}, picture ${screenshot != null}, audio ${clip != null}, " +
                        "sentenceAudio ${sentenceAudio is SentenceAudioAttachment.Attached}, translation ${FieldTemplate.SENTENCE_TRANSLATION in values}, forced $force",
                )
                if (result is AddResult.Added || result is AddResult.Updated) {
                    succeeded()
                    confirmedAudio?.let { audioEditor?.confirm(it) }
                }
                val (resultState, ids) = report(scan, noteTerm, result)
                opens = ids
                resultState
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(1000) { showState(scan, index, term, "", null) }
                }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adding a note failed", e.redacted())
                toast(context.getString(R.string.anki_error, e.message ?: e.javaClass.simpleName))
                "error"
            } finally {
                configurationWatch?.cancel()
                // A cancelled note does not wait for its translation or remaining attachment lookups.
                translated?.cancel()
                clipTask?.cancel()
                furiganaTask?.cancel()
                withContext(NonCancellable) {
                    screenshotTask?.cancelAndJoin()
                }
                (sentenceAudio as? SentenceAudioAttachment.Attached)?.file?.delete()
                image?.close()
                picture?.recycle()
                synchronized(createdScreenshots) { createdScreenshots.toList() }.forEach { it.delete() }
                screenshot?.delete()
            }
            val shown = showState(scan, index, term, state, opens)
            if (state == "added" && shown) afterCompletion { if (scan == scanNotes.scan) onNoteAdded() }
        }
    }

    /**
     * Shows a note's [state] on the entry of its [term]: the popup may show another word by now, or the same one at
     * another index. [opens] are the notes that entry's 📖 opens. Nothing is shown once the note's [scan] has closed.
     *
     * @return whether the popup still shows the entry.
     */
    private suspend fun showState(
        scan: Int,
        index: Int,
        term: Pair<String, String>?,
        state: String,
        opens: List<Long>?,
    ): Boolean {
        if (scan != scanNotes.scan) return false
        val entry = if (term == null) index else entryOf(currentTerms() ?: return false, index, term) ?: return false
        opens?.let { openableNotes[entry] = it }
        page.setNoteStates(mapOf(entry to state))
        return true
    }

    override fun onOpenNote(index: Int) {
        val ids = openableNotes[index] ?: return
        if (anki.browseNotes(ids)) onAnkiOpened() else toast(context.getString(R.string.anki_not_installed))
    }

    override fun onPlayAudio(index: Int, expression: String, reading: String) = play(expression, reading)

    override fun onOpenApp() = onOpenAnkiSettings()

    override fun onNoteWaiting() = toast(context.getString(R.string.overlay_note_wait))

    override fun onAudioMenu(view: Int, request: Int, expression: String, reading: String) =
        audioMenu.open(view, request, expression, reading, language())

    private suspend fun loadAudioMenu(expression: String, reading: String, language: Language): PopupAudioMenu.Clips {
        val sources = audioSettings.current(language).sources
        val candidates = audio.candidates(expression, reading, language, sources)
        val items = buildJsonArray {
            for (candidate in candidates) {
                add(buildJsonObject {
                    val source = sourceLabel(candidate, sources.size)
                    put("id", candidate.id)
                    put("label", candidate.name.ifEmpty { source })
                    put("detail", if (candidate.name.isEmpty()) hostOf(candidate.url) else source)
                })
            }
        }
        return PopupAudioMenu.Clips(candidates, items)
    }

    override fun onPlayClip(request: Int, clipId: String) {
        val (term, candidate) = audioMenu.choose(request, clipId) ?: return
        audioPlayback.request(term, rememberClip = { chosenClips[term] = it }) {
            if (candidate.isSpeech) {
                Pronunciation.Speech.of(term.first, term.second, candidate.language)
            } else {
                audio.download(candidate, term.first, term.second)?.let { Pronunciation.Clip(it) }
            }
        }
    }

    /** Plays the clip chosen for this term in the menu, or the first one the sources have. */
    private fun play(expression: String, reading: String) = audioPlayback.request(expression to reading) {
        chosenClips[expression to reading]?.let { Pronunciation.Clip(it) }
            ?: audio.pronunciation(expression, reading, language())
    }

    private fun setAudioFailure(failure: PopupAudioPlayback.Failure?) {
        audioToast?.cancel()
        val message = when (failure) {
            PopupAudioPlayback.Failure.NO_AUDIO -> R.string.audio_not_found
            PopupAudioPlayback.Failure.NO_VOICE -> R.string.audio_no_voice
            null -> null
        }
        audioToast = message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).also(Toast::show) }
    }

    fun release() {
        pendingNote.cancel()
        scanNotes.close()
        audioEditor?.release()
        cropEditor?.cancel()
        onResultsHidden()
        page.noteActions = null
    }

    fun releaseAudioContext(id: AudioContextId, cancelActive: Boolean = true) {
        audioEditor?.releaseContext(id, cancelActive)
        lastNoteConfig?.let { previous ->
            val config = JsonObject(previous + ("sentenceAudioEligible" to JsonPrimitive(false)))
            lastNoteConfig = config
            page.setNoteConfig(config)
        }
    }

    /**
     * `{sentence-furigana}` and `{sentence-furigana-plain}`: the sentence split into the longest terms the
     * dictionaries know, as Yomitan's scanning parser does, formatted by the page.
     */
    private suspend fun sentenceFurigana(sentence: String, noteLanguage: Language): Pair<String, String>? {
        val parts = parseSentence(sentence, noteLanguage) { text, language ->
            lookup.lookup(text, language, extraEntries = false).firstOrNull()
        }
        val formatted = evaluateJson<Map<String, String>>(
            "JSON.stringify(NoteData.sentenceFurigana(${json.encodeToString(parts)}, " +
                "${json.encodeToString(noteLanguage.support.languageTag)}))",
        ) ?: return null
        return formatted["html"].orEmpty() to formatted["plain"].orEmpty()
    }

    private fun currentClip(): ClipData? = runCatching {
        context.getSystemService(ClipboardManager::class.java)?.primaryClip
    }.getOrNull()

    private suspend fun readClipboardText(clip: ClipData?): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (clip == null || clip.itemCount == 0) return@withContext null
            val item = clip.getItemAt(0)
            // Skip image-only content URIs so their raw URI string is not pasted as clipboard-text.
            val uri = item.uri
            if (item.text.isNullOrEmpty() && uri != null && context.contentResolver.getType(uri)?.startsWith("image/") == true) {
                return@withContext null
            }
            item.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private suspend fun readClipboardImage(clip: ClipData?): String? {
        if (clip == null || clip.itemCount == 0) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = (0 until clip.itemCount).firstNotNullOfOrNull { i ->
                    clip.getItemAt(i).uri?.takeIf { context.contentResolver.getType(it)?.startsWith("image/") == true }
                } ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_CLIPBOARD_IMAGE_SIDE) {
                    sampleSize *= 2
                }
                val decode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
            }.getOrNull()
        } ?: return null
        var file: File? = null
        return try {
            file = saveScreenshot(bitmap)
            anki.addMedia(file, "screenlate_clip_${System.currentTimeMillis()}", AnkiDroid.MediaKind.IMAGE)
        } finally {
            bitmap.recycle()
            file?.delete()
        }
    }

    /** Copies glossary images into AnkiDroid and replaces their placeholders with the stored file names. */
    private suspend fun resolveGlossaryMedia(
        media: List<MediaRef>,
        values: MutableMap<String, String>,
        used: Set<String>,
        noteLanguage: Language,
    ) {
        val needed = values.filterKeys { it in used }.values.joinToString("")
        val stored = mutableMapOf<Pair<String, String>, String>()
        for (ref in media) {
            if (ref.placeholder !in needed) continue
            val name = stored.getOrPut(ref.dictionary to ref.path) {
                val bytes = lookup.media(ref.dictionary, ref.path, noteLanguage) ?: return@getOrPut ""
                val file = File(anki.mediaDirectory(), "${ref.path.hashCode().toUInt()}.${mediaExtension(ref.path)}")
                val markup = try {
                    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                    anki.addMedia(file, "screenlate_${file.nameWithoutExtension}", AnkiDroid.MediaKind.IMAGE)
                } finally {
                    file.delete()
                }
                markup?.let { IMG_SRC.find(it)?.groupValues?.get(1) }.orEmpty()
            }
            values.replaceAll { _, value -> value.replace(ref.placeholder, name) }
        }
    }

    /** Encodes the note's picture, scaled down if a side is too long for WebP (a long imported screenshot). */
    private suspend fun saveScreenshot(bitmap: Bitmap, onCreated: ((File) -> Unit)? = null): File = withContext(Dispatchers.IO) {
        val (width, height) = webpSize(bitmap.width, bitmap.height)
        val fitted = if (width == bitmap.width && height == bitmap.height) bitmap else bitmap.scale(width, height)
        try {
            val mediaDir = anki.mediaDirectory().apply { mkdirs() }
            File.createTempFile("screenshot_", ".webp", mediaDir).also { file ->
                onCreated?.invoke(file)
                try {
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    val written = file.outputStream().use { fitted.compress(format, SCREENSHOT_QUALITY, it) }
                    if (!written) {
                        throw IOException(context.getString(R.string.anki_picture_failed))
                    }
                } catch (t: Throwable) {
                    file.delete()
                    throw t
                }
            }
        } finally {
            if (fitted !== bitmap) fitted.recycle()
        }
    }

    /** The entry state after an add, and the notes its 📖 opens. */
    private fun report(scan: Int, term: Pair<String, String>, result: AddResult): Pair<String, List<Long>?> =
        when (result) {
            is AddResult.Added -> remember(scan, term, listOf(result.noteId))
            is AddResult.Updated -> remember(scan, term, listOf(result.noteId))
            is AddResult.Duplicate -> "open" to result.noteIds
            AddResult.ConfigurationChanged -> {
                toast(context.getString(R.string.anki_operation_settings_changed))
                "" to null
            }
            AddResult.NotConfigured -> {
                toast(context.getString(R.string.anki_not_configured))
                "error" to null
            }
            is AddResult.Unavailable -> {
                toast(
                    context.getString(
                        if (result.availability == AnkiAvailability.NOT_INSTALLED) R.string.anki_not_installed else R.string.anki_no_permission,
                    ),
                )
                "error" to null
            }
            AddResult.Rejected -> {
                Log.w(TAG, "AnkiDroid rejected the note")
                toast(context.getString(R.string.anki_error, context.getString(R.string.anki_rejected)))
                "error" to null
            }
            is AddResult.Failed -> {
                toast(context.getString(R.string.anki_error, result.message))
                "error" to null
            }
        }

    private fun remember(scan: Int, term: Pair<String, String>, ids: List<Long>): Pair<String, List<Long>> {
        scanNotes.add(scan, term, ids)
        return "added" to ids
    }

    private fun sourceLabel(candidate: AudioCandidate, sourceCount: Int): String {
        val name = context.getString(candidate.source.type.label)
        return if (sourceCount > 1) "${candidate.sourceIndex + 1}. $name" else name
    }

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @Serializable
    private data class NoteDataDto(
        val values: Map<String, String>,
        val media: List<MediaRef> = emptyList(),
        val term: TermDto,
    )

    @Serializable
    private data class TermDto(val expression: String, val reading: String = "")

    @Serializable
    private data class MediaRef(val dictionary: String, val path: String, val placeholder: String)

    private companion object {
        const val TAG = "PopupNotes"
        const val DUPLICATE_CHECK_DELAY_MS = 100L
        const val MAX_CLIPBOARD_IMAGE_SIDE = 1920
        const val ANKI_STATUS_TTL_MS = 5_000L
        const val AUTO_PLAY_DELAY_MS = 500L
        const val SCREENSHOT_QUALITY = 85
        val IMG_SRC = Regex("""src="([^"]+)"""")
    }
}
