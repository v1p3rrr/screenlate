package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import com.vpr.screenlate.core.anki.AddResult
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.NoteRequest
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.ui.CropEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Where the looked-up word came from: its sentence and a clean screenshot.
 *
 * @property screenshotLeft screen position of the screenshot's left edge.
 * @property focus the word's paragraph in screen coordinates, the initial crop frame.
 */
data class NoteContext(
    val sentence: Sentence?,
    val screenshot: Bitmap?,
    val screenshotLeft: Float = 0f,
    val screenshotTop: Float = 0f,
    val focus: RectF? = null,
)

/**
 * The ➕ and 🔊 buttons of a [LookupPage]: duplicate marks, adding notes to AnkiDroid, playing and auto-playing audio.
 *
 * @param noteContext waits for the final OCR result and describes where the word came from.
 */
class PopupNotes(
    private val context: Context,
    private val scope: CoroutineScope,
    private val page: LookupPage,
    private val anki: AnkiDroid,
    private val notes: AnkiNotes,
    private val audio: AudioFinder,
    private val audioSettings: AudioSettingsRepository,
    private val lookup: DictionaryLookup,
    private val noteContext: suspend () -> NoteContext,
    private val cropEditor: CropEditor?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var duplicateJob: Job? = null
    private var player: MediaPlayer? = null
    private var lastAutoPlayed: Pair<String, String>? = null
    private var autoPlayJob: Job? = null
    private var pendingAutoPlay: Pair<String, String>? = null

    /** Shows or hides the entry buttons according to the current settings. Call before showing results. */
    suspend fun refreshActions() {
        val settings = notes.settings()
        val ankiReady = settings.configured && anki.availability() == AnkiAvailability.READY
        val audioEnabled = audioSettings.current().sources.isNotEmpty()
        page.setActions(anki = ankiReady, audio = audioEnabled)
    }

    /** Called after a view with results is shown: schedules the duplicate check and auto-play. */
    fun onResultsShown(firstTerm: Pair<String, String>?) {
        duplicateJob?.cancel()
        duplicateJob = scope.launch {
            delay(DUPLICATE_CHECK_DELAY_MS)
            markDuplicates()
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

    fun onClosed() {
        duplicateJob?.cancel()
        autoPlayJob?.cancel()
        pendingAutoPlay = null
        lastAutoPlayed = null
    }

    private suspend fun markDuplicates() {
        val settings = notes.settings()
        if (!settings.configured || !settings.duplicateCheck) return
        val raw = page.evaluate("JSON.stringify(Popup.allNoteData())") ?: return
        val entries = runCatching {
            // evaluateJavascript returns the string result JSON-encoded once more.
            json.decodeFromString<List<Map<String, String>>>(json.decodeFromString<String>(raw))
        }.getOrElse { return }
        val states = entries.withIndex().associate { (index, values) ->
            val duplicate = runCatching { notes.isDuplicate(values) }.getOrDefault(false)
            index to when {
                !duplicate -> ""
                settings.duplicateBehavior == DuplicateBehavior.PREVENT -> "blocked"
                else -> "duplicate"
            }
        }
        page.setNoteStates(states.filterValues { it.isNotEmpty() })
    }

    fun add(index: Int, noteDataJson: String, withScreenshot: Boolean) {
        scope.launch {
            val state = try {
                val data = json.decodeFromString<NoteDataDto>(noteDataJson)
                val used = notes.usedMarkers()
                val context = noteContext()
                val values = data.values.toMutableMap()
                context.sentence?.let { sentence ->
                    values["sentence"] = sentence.text
                    values["cloze-prefix"] = sentence.prefix
                    values["cloze-body"] = sentence.body
                    values["cloze-suffix"] = sentence.suffix
                }
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
                val clip = if ("audio" in used) {
                    audio.find(values["expression"].orEmpty(), values["reading"].orEmpty())
                } else {
                    null
                }
                val result = notes.add(NoteRequest(values, screenshot, clip))
                screenshot?.delete()
                report(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adding a note failed", e)
                toast(context.getString(R.string.anki_error, e.message ?: e.javaClass.simpleName))
                "error"
            }
            page.setNoteStates(mapOf(index to state))
        }
    }

    fun play(expression: String, reading: String) {
        scope.launch {
            val clip = audio.find(expression, reading)
            if (clip == null) {
                toast(context.getString(R.string.audio_not_found))
                return@launch
            }
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                runCatching {
                    setDataSource(clip.file.absolutePath)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { it.reset() }
                    prepareAsync()
                }.onFailure { Log.w(TAG, "Cannot play ${clip.url}", it) }
            }
        }
    }

    fun release() {
        cropEditor?.dismiss()
        duplicateJob?.cancel()
        player?.release()
        player = null
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

    private fun report(result: AddResult): String = when (result) {
        is AddResult.Added, is AddResult.Updated -> "added"
        AddResult.Duplicate -> "blocked"
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
            Log.w(TAG, "AnkiDroid rejected the note: ${result.message}")
            toast(context.getString(R.string.anki_error, result.message))
            "error"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @Serializable
    private data class NoteDataDto(val values: Map<String, String>, val media: List<MediaRef> = emptyList())

    @Serializable
    private data class MediaRef(val dictionary: String, val path: String, val placeholder: String)

    private companion object {
        const val TAG = "PopupNotes"
        const val DUPLICATE_CHECK_DELAY_MS = 300L
        const val AUTO_PLAY_DELAY_MS = 500L
        const val SCREENSHOT_QUALITY = 85
        val IMG_SRC = Regex("""src="([^"]+)"""")
    }
}
