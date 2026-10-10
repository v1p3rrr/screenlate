package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.widget.Toast
import com.vpr.screenlate.core.anki.AddResult
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.NoteRequest
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
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.capture.SharedScreenshot
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.overlay.ui.CropEditor
import com.vpr.screenlate.overlay.web.LookupPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import com.vpr.screenlate.core.common.redacted

/**
 * Where the looked-up word came from: its sentence and a clean screenshot.
 *
 * @property screenshot taken for the note, which releases it when done.
 * @property focus the word's paragraph in screen coordinates, the initial crop frame.
 * @property documentTitle name of the app the word was found in, for `{document-title}`.
 */
data class NoteContext(
    val sentence: Sentence?,
    val screenshot: SharedScreenshot?,
    val focus: RectF? = null,
    val documentTitle: String = "",
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

/** Where a word came from, taken when its ➕ is pressed, so a view shown later does not change the note. */
fun interface NoteSource {
    /** Called once per note; [screenshot] asks for the screen picture. */
    suspend fun context(screenshot: Boolean): NoteContext
}

/**
 * The ➕/📖 and 🔊 buttons of a [LookupPage]: duplicate marks, adding notes to AnkiDroid, opening added notes,
 * playing and auto-playing audio, and the clip menu.
 *
 * Words added during one scan remember their notes until [onClosed], so their button opens the note instead of
 * adding it again.
 *
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
    private val noteSource: () -> NoteSource,
    private val translation: SentenceTranslation,
    private val cropEditor: CropEditor?,
    private val onAnkiOpened: () -> Unit,
    private val onNoteAdded: () -> Unit,
    private val onOpenAnkiSettings: () -> Unit,
) : LookupPage.NoteActions {
    private val json = Json { ignoreUnknownKeys = true }
    private var duplicateJob: Job? = null
    private var ankiStatus: AnkiStatus? = null
    private var ankiStatusAt = 0L
    private var lastAutoPlayed: Pair<String, String>? = null
    private var autoPlayJob: Job? = null
    private var pendingAutoPlay: Pair<String, String>? = null
    private var audioMenuJob: Job? = null

    private val scanNotes = ScanNotes()

    /** Notes that the 📖 buttons of the current view open, by entry index. */
    private val openableNotes = mutableMapOf<Int, List<Long>>()

    /** Clips offered by the last audio menu, and the clip chosen for each term; the choice goes into `{audio}`. */
    private var menuCandidates: List<AudioCandidate> = emptyList()
    private val chosenClips = mutableMapOf<Pair<String, String>, AudioClip>()

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
        // The frequency modes also order each dictionary's frequency values in the popup, with or without Anki.
        val config = buildJsonObject {
            put("markers", if (ankiReady) buildJsonArray { notes.usedMarkers(language()).forEach { add(JsonPrimitive(it)) } } else JsonNull)
            put("frequencyModes", buildJsonObject { lookup.frequencyModes().forEach { (title, mode) -> put(title, mode) } })
        }
        page.setNoteConfig(config)
    }

    /** The Anki setup is checked against AnkiDroid at most every few seconds, not for every word. */
    private suspend fun ankiStatus(): AnkiStatus {
        val now = System.currentTimeMillis()
        ankiStatus?.takeIf { now - ankiStatusAt < ANKI_STATUS_TTL_MS }?.let { return it }
        return notes.status(language()).also {
            ankiStatus = it
            ankiStatusAt = now
        }
    }

    /** Called after a view with results is shown: schedules the duplicate check and auto-play. */
    fun onResultsShown(firstTerm: Pair<String, String>?) {
        openableNotes.clear()
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
        openableNotes.clear()
        audioMenuJob?.cancel()
        duplicateJob?.cancel()
        duplicateJob = scope.launch { markNotes() }
    }

    /** The popup went away without closing the scan, e.g. the aim moved to text without results. */
    fun onResultsHidden() {
        duplicateJob?.cancel()
        autoPlayJob?.cancel()
        audioMenuJob?.cancel()
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
        cropEditor?.cancel()
        onResultsHidden()
        ankiStatus = null
        lastAutoPlayed = null
        scanNotes.close()
        openableNotes.clear()
        chosenClips.clear()
    }

    /** Words added during this scan get 📖; duplicates get 📖 or a mark, depending on the duplicate behavior. */
    private suspend fun markNotes() {
        val settings = notes.settings(language())
        if (ankiStatus() != AnkiStatus.Ready) return
        val terms = currentTerms() ?: return
        val states = mutableMapOf<Int, String>()
        terms.forEachIndexed { index, term ->
            scanNotes[term]?.let { ids ->
                openableNotes[index] = ids
                states[index] = "added"
            }
        }
        if (settings.duplicateCheck) {
            val markers = notes.duplicateCheckMarkers(language())
            val entries = evaluateJson<List<Map<String, String>>>(
                "JSON.stringify(Popup.allNoteData(${Json.encodeToString(markers.toList())}))",
            ).orEmpty()
            entries.forEachIndexed { index, values ->
                if (index in states) return@forEachIndexed
                val ids = try {
                    notes.duplicateIds(language(), values)
                } catch (e: CancellationException) {
                    // The view changed: its marks must not reach the next one.
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Duplicate check failed", e.redacted())
                    emptyList()
                }
                if (ids.isEmpty()) return@forEachIndexed
                if (settings.duplicateBehavior == DuplicateBehavior.PREVENT) {
                    openableNotes[index] = ids
                    states[index] = "open"
                } else {
                    states[index] = "duplicate"
                }
            }
        }
        page.setNoteStates(states)
    }

    private suspend fun currentTerms(): List<Pair<String, String>>? =
        evaluateJson<List<List<String>>>("JSON.stringify(Popup.terms())")
            ?.map { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }

    /** evaluateJavascript returns a string result JSON-encoded once more. */
    private suspend inline fun <reified T> evaluateJson(script: String): T? {
        val raw = page.evaluate(script) ?: return null
        return runCatching { json.decodeFromString<T>(json.decodeFromString<String>(raw)) }.getOrNull()
    }

    override fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean, force: Boolean) {
        val source = noteSource()
        val scan = scanNotes.scan
        // The note keeps the language it was asked for, even if the scan closes before it is added.
        val noteLanguage = language()
        scope.launch {
            var shared: SharedScreenshot? = null
            var picture: Bitmap? = null
            var term: Pair<String, String>? = null
            var opens: List<Long>? = null
            var screenshot: File? = null
            var translated: Deferred<String?>? = null
            val state = try {
                val data = json.decodeFromString<NoteDataDto>(noteData)
                val noteTerm = data.term.expression to data.term.reading
                term = noteTerm
                val used = notes.usedMarkers(noteLanguage)
                val context = source.context(withScreenshot && "screenshot" in used)
                shared = context.screenshot
                // Asked now, so it comes while the crop editor is open; the wait counts from when the note is ready.
                val sentence = context.sentence?.text
                if (FieldTemplate.SENTENCE_TRANSLATION in used && sentence != null) {
                    translated = async { translation.forNote(sentence, noteLanguage) }
                }
                // The editor opens first, while the scan it shows is still on the screen.
                val shot = context.screenshot
                if (shot != null && cropEditor != null) {
                    // Cancelling the editor cancels the note.
                    picture = cropEditor.edit(shot.bitmap, shot.screen.left.toFloat(), shot.screen.top.toFloat(), context.focus)
                        ?: run {
                            showState(scan, index, noteTerm, "", null)
                            return@launch
                        }
                }
                // The crop is a bitmap of its own.
                shared?.release()
                shared = null
                val values = data.values.toMutableMap()
                context.sentence?.let { sentence ->
                    values["sentence"] = escapeHtml(sentence.text)
                    values["cloze-prefix"] = escapeHtml(sentence.prefix)
                    values["cloze-body"] = escapeHtml(sentence.body)
                    values["cloze-suffix"] = escapeHtml(sentence.suffix)
                    if ("sentence-furigana" in used || "sentence-furigana-plain" in used) {
                        sentenceFurigana(sentence.text, noteLanguage)?.let { (html, plain) ->
                            values["sentence-furigana"] = html
                            values["sentence-furigana-plain"] = plain
                        }
                    }
                }
                values["document-title"] = escapeHtml(context.documentTitle)
                translated?.let { withTimeoutOrNull(SentenceTranslation.NOTE_WAIT_MS) { it.await() } }
                    ?.let { values[FieldTemplate.SENTENCE_TRANSLATION] = escapeHtml(it) }
                resolveGlossaryMedia(data.media, values, used, noteLanguage)
                screenshot = picture?.let { saveScreenshot(it) }
                val clip = if ("audio" in used) chosenClips[noteTerm] ?: audio.find(noteTerm.first, noteTerm.second, noteLanguage) else null
                val result = notes.add(noteLanguage, NoteRequest(values, screenshot, clip), force)
                // The kind of result only: messages and fields may carry the note's text.
                Log.i(
                    TAG,
                    "Note: ${result.javaClass.simpleName}, picture ${screenshot != null}, audio ${clip != null}, " +
                        "translation ${FieldTemplate.SENTENCE_TRANSLATION in values}, forced $force",
                )
                val (resultState, ids) = report(scan, noteTerm, result)
                opens = ids
                resultState
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adding a note failed", e.redacted())
                toast(context.getString(R.string.anki_error, e.message ?: e.javaClass.simpleName))
                "error"
            } finally {
                // A cancelled note does not wait for its translation.
                translated?.cancel()
                shared?.release()
                picture?.recycle()
                screenshot?.delete()
            }
            val shown = showState(scan, index, term, state, opens)
            if (state == "added" && shown) onNoteAdded()
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

    override fun onAudioMenu(index: Int, expression: String, reading: String) {
        audioMenuJob?.cancel()
        page.showAudioMenu(index, buildJsonArray { }, loading = true)
        audioMenuJob = scope.launch {
            val candidates = audio.candidates(expression, reading, language())
            menuCandidates = candidates
            val sources = audioSettings.current(language()).sources
            val items = buildJsonArray {
                for (candidate in candidates) {
                    add(
                        buildJsonObject {
                            put("id", candidate.id)
                            put("label", candidate.name.ifEmpty { sourceLabel(candidate, sources.size) })
                            put("detail", if (candidate.name.isEmpty()) hostOf(candidate.url) else sourceLabel(candidate, sources.size))
                        },
                    )
                }
            }
            page.showAudioMenu(index, items, loading = false)
        }
    }

    override fun onPlayClip(index: Int, clipId: String) {
        val candidate = menuCandidates.firstOrNull { it.id == clipId } ?: return
        scope.launch {
            val terms = currentTerms() ?: return@launch
            val term = terms.getOrNull(index) ?: return@launch
            if (candidate.isSpeech) {
                if (!player.play(Pronunciation.Speech(term.second.ifEmpty { term.first }, language()))) {
                    toast(context.getString(R.string.audio_no_voice))
                }
                return@launch
            }
            val clip = audio.download(candidate, term.first, term.second)
            if (clip == null) {
                toast(context.getString(R.string.audio_not_found))
                return@launch
            }
            chosenClips[term] = clip
            player.play(Pronunciation.Clip(clip))
        }
    }

    /** Plays the clip chosen for this term in the menu, or the first one the sources have. */
    fun play(expression: String, reading: String) {
        scope.launch {
            val pronunciation = chosenClips[expression to reading]?.let { Pronunciation.Clip(it) }
                ?: audio.pronunciation(expression, reading, language())
            if (pronunciation == null) {
                toast(context.getString(R.string.audio_not_found))
                return@launch
            }
            if (!player.play(pronunciation) && pronunciation is Pronunciation.Speech) {
                toast(context.getString(R.string.audio_no_voice))
            }
        }
    }

    fun release() {
        cropEditor?.cancel()
        onResultsHidden()
        page.noteActions = null
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
            "JSON.stringify(NoteData.sentenceFurigana(${json.encodeToString(parts)}))",
        ) ?: return null
        return formatted["html"].orEmpty() to formatted["plain"].orEmpty()
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

    private suspend fun saveScreenshot(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        File(anki.mediaDirectory(), "screenshot_${System.currentTimeMillis()}.webp").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, SCREENSHOT_QUALITY, it) }
        }
    }

    /** The entry state after an add, and the notes its 📖 opens. */
    private fun report(scan: Int, term: Pair<String, String>, result: AddResult): Pair<String, List<Long>?> =
        when (result) {
            is AddResult.Added -> remember(scan, term, listOf(result.noteId))
            is AddResult.Updated -> remember(scan, term, listOf(result.noteId))
            is AddResult.Duplicate -> "open" to result.noteIds
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
        const val DUPLICATE_CHECK_DELAY_MS = 300L
        const val ANKI_STATUS_TTL_MS = 5_000L
        const val AUTO_PLAY_DELAY_MS = 500L
        const val SCREENSHOT_QUALITY = 85
        val IMG_SRC = Regex("""src="([^"]+)"""")
    }
}
