package com.vpr.screenlate.overlay.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.overlay.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Colors of the lookup page; e-ink also turns off its animations. */
enum class PageTheme(val css: String) { LIGHT("light"), DARK("dark"), E_INK("eink") }

/**
 * Builds the JSON state that `Popup.render` expects (see the comment at the top of popup.js).
 *
 * The result can be large (Jitendex entries), so it is written in one pass from data classes; call it off the
 * main thread where possible.
 */
object PageState {
    private val json = Json { encodeDefaults = true }

    /**
     * @param matched length of the matched prefix of [text] in code points.
     * @param pending OCR is still refining the text; shows a spinner.
     * @param engine OCR engine label, empty to hide.
     * @param kanji shown instead of [results] and [message]: the first character's kanji entry when no word was found.
     */
    fun build(
        context: Context,
        theme: PageTheme,
        text: String,
        matched: Int,
        results: List<LookupResult>,
        message: String?,
        pending: Boolean = false,
        engine: String = "",
        hideSource: Boolean = false,
        ocrError: String = "",
        noteWait: Boolean = false,
        kanji: KanjiResult? = null,
        sentence: String = "",
    ): String = json.encodeToString(
        StateDto(
            theme = theme.css,
            pending = pending,
            noteWait = noteWait,
            engine = engine,
            ocrError = ocrError,
            hideSource = hideSource,
            source = SourceDto(text, matched),
            results = results,
            kanji = kanji,
            message = message.takeIf { kanji == null },
            labels = if (kanji != null) labels(context) + kanjiLabels(context) else labels(context),
            sentence = sentence,
        ),
    )

    /** Strings of the entries and of the page itself (Copy over a selection), which every view needs. */
    private fun labels(context: Context): Map<String, String> = mapOf(
        "noResults" to context.getString(R.string.overlay_no_results),
        "addNote" to context.getString(R.string.overlay_add_note),
        "noteWait" to context.getString(R.string.overlay_note_wait),
        "playAudio" to context.getString(R.string.overlay_play_audio),
        "copy" to context.getString(R.string.overlay_copy),
        "copyDefinition" to context.getString(R.string.overlay_copy_definition),
        "copyTranslation" to context.getString(R.string.overlay_copy_translation),
        "openNote" to context.getString(R.string.overlay_open_note),
        "openApp" to context.getString(R.string.overlay_open_app),
        "addAnyway" to context.getString(R.string.overlay_add_anyway),
        "addAnywayWithPicture" to context.getString(R.string.overlay_add_anyway_picture),
        "audioLoading" to context.getString(R.string.overlay_audio_loading),
        "audioNone" to context.getString(R.string.audio_not_found),
        "close" to context.getString(R.string.overlay_close),
        "pitchDictionaries" to context.getString(R.string.overlay_pitch_dictionaries),
        "ocrError" to context.getString(R.string.overlay_ocr_error_title),
        "translate" to context.getString(R.string.overlay_translate),
        "translating" to context.getString(R.string.overlay_translating),
    )

    /** A kanji view; [kanji] without entries shows [message] instead. */
    fun kanji(context: Context, theme: PageTheme, kanji: KanjiResult, message: String?): String = json.encodeToString(
        StateDto(
            theme = theme.css,
            source = SourceDto(kanji.character, 1),
            kanji = kanji.takeIf { it.entries.isNotEmpty() },
            message = message.takeIf { kanji.entries.isEmpty() },
            labels = labels(context) + kanjiLabels(context),
        ),
    )

    private fun kanjiLabels(context: Context): Map<String, String> = mapOf(
        "onyomi" to context.getString(R.string.overlay_kanji_onyomi),
        "kunyomi" to context.getString(R.string.overlay_kanji_kunyomi),
        "stat_strokes" to context.getString(R.string.overlay_kanji_strokes),
        "stat_grade" to context.getString(R.string.overlay_kanji_grade),
        "stat_jlpt" to context.getString(R.string.overlay_kanji_jlpt),
        "stat_freq" to context.getString(R.string.overlay_kanji_frequency),
    )

    /** Length of the first result's match in code points, for highlighting; one character for a [kanji] entry. */
    fun matchedLength(results: List<LookupResult>, kanji: KanjiResult? = null): Int =
        if (kanji != null) 1 else results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0

    /**
     * Puts [text] on the clipboard, with [html] for apps that paste formatted text (others take the text); Android
     * shows its own confirmation.
     */
    fun copy(context: Context, text: String, html: String? = null) {
        val label = context.getString(R.string.overlay_copy)
        val clip = if (html.isNullOrEmpty()) ClipData.newPlainText(label, text) else ClipData.newHtmlText(label, text, html)
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }

    @Serializable
    private data class StateDto(
        val theme: String,
        val pending: Boolean = false,
        /** ➕ is grey and only explains itself until the scan's final text (`noteWaitsForText`). */
        val noteWait: Boolean = false,
        val engine: String = "",
        /** Why cloud recognition failed for this scan, shown behind ⚠; empty without a failure. */
        val ocrError: String = "",
        /** The popup starts with the first entry instead of the recognized text (see `Popup` in popup.js). */
        val hideSource: Boolean = false,
        val source: SourceDto,
        val results: List<LookupResult> = emptyList(),
        val kanji: KanjiResult? = null,
        val message: String? = null,
        val labels: Map<String, String> = emptyMap(),
        /** The sentence around the word; a new scan result with the same word and sentence keeps the translation. */
        val sentence: String = "",
    )

    @Serializable
    private data class SourceDto(val text: String, val matched: Int)
}
