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
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.ui.CropEditor
import com.vpr.screenlate.overlay.web.LookupPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import com.vpr.screenlate.core.common.redacted

/**
 * Where the looked-up word came from: its sentence and a clean screenshot.
 *
 * @property screenshotLeft screen position of the screenshot's left edge.
 * @property focus the word's paragraph in screen coordinates, the initial crop frame.
 * @property documentTitle name of the app the word was found in, for `{document-title}`.
 */
data class NoteContext(
    val sentence: Sentence?,
    val screenshot: Bitmap?,
    val screenshotLeft: Float = 0f,
    val screenshotTop: Float = 0f,
    val focus: RectF? = null,
    val documentTitle: String = "",
)

/**
 * The ➕/📖 and 🔊 buttons of a [LookupPage]: duplicate marks, adding notes to AnkiDroid, opening added notes,
 * playing and auto-playing audio, and the clip menu.
 *
 * Words added during one scan remember their notes until [onClosed], so their button opens the note instead of
 * adding it again.
 *
 * @param noteContext waits for the final OCR result and describes where the word came from.
 * @param onAnkiOpened called after a note was opened in AnkiDroid, e.g. to dock the bubble.
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
    private val language: Language,
    private val noteContext: suspend () -> NoteContext,
    private val cropEditor: CropEditor?,
    private val onAnkiOpened: () -> Unit,
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

    /** Notes added during this scan, by term. */
    private val addedThisScan = mutableMapOf<Pair<String, String>, List<Long>>()

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
        val audioEnabled = audioSettings.current().sources.isNotEmpty()
        val problem = (status as? AnkiStatus.Broken)?.problem?.let { context.getString(it.message) }
        page.setActions(anki = ankiReady, audio = audioEnabled, ankiProblem = problem)
        if (ankiReady) {
            val config = buildJsonObject {
                put("markers", buildJsonArray { notes.usedMarkers().forEach { add(JsonPrimitive(it)) } })
                put("frequencyModes", buildJsonObject { lookup.frequencyModes().forEach { (title, mode) -> put(title, mode) } })
            }
            page.setNoteConfig(config)
        }
    }

    /** The Anki setup is checked against AnkiDroid at most every few seconds, not for every word. */
    private suspend fun ankiStatus(): AnkiStatus {
        val now = System.currentTimeMillis()
        ankiStatus?.takeIf { now - ankiStatusAt < ANKI_STATUS_TTL_MS }?.let { return it }
        return notes.status().also {
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
        if (audioSettings.current().autoPlay) play(term.first, term.second)
    }

    /** The scan ended (bubble docked or screen left): forget notes added and clips chosen during it. */
    fun onClosed() {
        onResultsHidden()
        ankiStatus = null
        lastAutoPlayed = null
        addedThisScan.clear()
        openableNotes.clear()
        chosenClips.clear()
    }

    /** Words added during this scan get 📖; duplicates get 📖 or a mark, depending on the duplicate behavior. */
    private suspend fun markNotes() {
        val settings = notes.settings()
        if (ankiStatus() != AnkiStatus.Ready) return
        val terms = currentTerms() ?: return
        val states = mutableMapOf<Int, String>()
        terms.forEachIndexed { index, term ->
            addedThisScan[term]?.let { ids ->
                openableNotes[index] = ids
                states[index] = "added"
            }
        }
        if (settings.duplicateCheck) {
            val markers = notes.duplicateCheckMarkers()
            val entries = evaluateJson<List<Map<String, String>>>(
                "JSON.stringify(Popup.allNoteData(${Json.encodeToString(markers.toList())}))",
            ).orEmpty()
            entries.forEachIndexed { index, values ->
                if (index in states) return@forEachIndexed
                val ids = runCatching { notes.duplicateIds(values) }.getOrDefault(emptyList())
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
        scope.launch {
            val state = try {
                val data = json.decodeFromString<NoteDataDto>(noteData)
                val term = data.term.expression to data.term.reading
                val used = notes.usedMarkers()
                val context = noteContext()
                val values = data.values.toMutableMap()
                context.sentence?.let { sentence ->
                    values["sentence"] = escapeHtml(sentence.text)
                    values["cloze-prefix"] = escapeHtml(sentence.prefix)
                    values["cloze-body"] = escapeHtml(sentence.body)
                    values["cloze-suffix"] = escapeHtml(sentence.suffix)
                    if ("sentence-furigana" in used || "sentence-furigana-plain" in used) {
                        sentenceFurigana(sentence.text)?.let { (html, plain) ->
                            values["sentence-furigana"] = html
                            values["sentence-furigana-plain"] = plain
                        }
                    }
                }
                values["document-title"] = escapeHtml(context.documentTitle)
                resolveGlossaryMedia(data.media, values, used)
                val picture = if (withScreenshot && "screenshot" in used) {
                    val image = context.screenshot?.takeUnless { it.isRecycled }
                    if (image != null && cropEditor != null) {
                        // Cancelling the editor cancels the note.
                        cropEditor.edit(image, context.screenshotLeft, context.screenshotTop, context.focus)
                            ?: return@launch page.setNoteStates(mapOf(index to ""))
                    } else {
                        null
                    }
                } else {
                    null
                }
                val screenshot = picture?.let { saveScreenshot(it).also { _ -> it.recycle() } }
                val clip = if ("audio" in used) chosenClips[term] ?: audio.find(term.first, term.second, language) else null
                val result = notes.add(NoteRequest(values, screenshot, clip), force)
                screenshot?.delete()
                report(index, term, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adding a note failed", e.redacted())
                toast(context.getString(R.string.anki_error, e.message ?: e.javaClass.simpleName))
                "error"
            }
            page.setNoteStates(mapOf(index to state))
        }
    }

    override fun onOpenNote(index: Int) {
        val ids = openableNotes[index] ?: return
        if (anki.browseNotes(ids)) onAnkiOpened() else toast(context.getString(R.string.anki_not_installed))
    }

    override fun onPlayAudio(index: Int, expression: String, reading: String) = play(expression, reading)

    override fun onOpenApp() = onOpenAnkiSettings()

    override fun onAudioMenu(index: Int, expression: String, reading: String) {
        audioMenuJob?.cancel()
        page.showAudioMenu(index, buildJsonArray { }, loading = true)
        audioMenuJob = scope.launch {
            val candidates = audio.candidates(expression, reading, language)
            menuCandidates = candidates
            val sources = audioSettings.current().sources
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
                if (!player.play(Pronunciation.Speech(term.second.ifEmpty { term.first }, language))) {
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
                ?: audio.pronunciation(expression, reading, language)
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
        cropEditor?.dismiss()
        duplicateJob?.cancel()
        audioMenuJob?.cancel()
        page.noteActions = null
    }

    /**
     * `{sentence-furigana}` and `{sentence-furigana-plain}`: the sentence split into the longest terms the
     * dictionaries know, as Yomitan's scanning parser does, formatted by the page.
     */
    private suspend fun sentenceFurigana(sentence: String): Pair<String, String>? {
        val parts = mutableListOf<SentencePart>()
        var offset = 0
        while (offset < sentence.length) {
            val result = runCatching { lookup.lookup(sentence.substring(offset), language, extraEntries = false) }
                .getOrDefault(emptyList())
                .firstOrNull()
            val matched = result?.matched?.takeIf { it.isNotEmpty() && sentence.startsWith(it, offset) }
            if (result != null && matched != null) {
                parts += SentencePart(matched, result.term.expression, result.term.reading)
                offset += matched.length
            } else {
                val next = sentence.offsetByCodePoints(offset, 1)
                val text = sentence.substring(offset, next)
                val last = parts.lastOrNull()
                if (last != null && last.expression == null) parts[parts.lastIndex] = last.copy(text = last.text + text)
                else parts += SentencePart(text)
                offset = next
            }
        }
        val formatted = evaluateJson<Map<String, String>>(
            "JSON.stringify(NoteData.sentenceFurigana(${json.encodeToString(parts)}))",
        ) ?: return null
        return formatted["html"].orEmpty() to formatted["plain"].orEmpty()
    }

    /** Copies glossary images into AnkiDroid and replaces their placeholders with the stored file names. */
    private suspend fun resolveGlossaryMedia(media: List<MediaRef>, values: MutableMap<String, String>, used: Set<String>) {
        val needed = values.filterKeys { it in used }.values.joinToString("")
        val stored = mutableMapOf<Pair<String, String>, String>()
        for (ref in media) {
            if (ref.placeholder !in needed) continue
            val name = stored.getOrPut(ref.dictionary to ref.path) {
                val bytes = lookup.media(ref.dictionary, ref.path) ?: return@getOrPut ""
                val file = File(anki.mediaDirectory(), "${ref.path.hashCode().toUInt()}.${ref.path.substringAfterLast('.', "png")}")
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                val markup = anki.addMedia(file, "screenlate_${file.nameWithoutExtension}", AnkiDroid.MediaKind.IMAGE)
                file.delete()
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

    private fun report(index: Int, term: Pair<String, String>, result: AddResult): String = when (result) {
        is AddResult.Added -> remember(index, term, listOf(result.noteId))
        is AddResult.Updated -> remember(index, term, listOf(result.noteId))
        is AddResult.Duplicate -> {
            openableNotes[index] = result.noteIds
            "open"
        }
        AddResult.NotConfigured -> {
            toast(context.getString(R.string.anki_not_configured))
            "error"
        }
        is AddResult.Unavailable -> {
            toast(
                context.getString(
                    if (result.availability == AnkiAvailability.NOT_INSTALLED) R.string.anki_not_installed else R.string.anki_no_permission,
                ),
            )
            "error"
        }
        is AddResult.Failed -> {
            Log.w(TAG, "AnkiDroid rejected the note")
            toast(context.getString(R.string.anki_error, result.message))
            "error"
        }
    }

    private fun remember(index: Int, term: Pair<String, String>, ids: List<Long>): String {
        addedThisScan[term] = ids
        openableNotes[index] = ids
        return "added"
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

    @Serializable
    private data class SentencePart(val text: String, val expression: String? = null, val reading: String? = null)

    private companion object {
        const val TAG = "PopupNotes"
        const val DUPLICATE_CHECK_DELAY_MS = 300L
        const val ANKI_STATUS_TTL_MS = 5_000L
        const val AUTO_PLAY_DELAY_MS = 500L
        const val SCREENSHOT_QUALITY = 85
        val IMG_SRC = Regex("""src="([^"]+)"""")
    }
}
