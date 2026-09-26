package com.vpr.screenlate.overlay.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.overlay.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
     */
    fun build(
        context: Context,
        dark: Boolean,
        text: String,
        matched: Int,
        results: List<LookupResult>,
        message: String?,
        pending: Boolean = false,
        engine: String = "",
    ): String = json.encodeToString(
        StateDto(
            theme = theme(dark),
            pending = pending,
            engine = engine,
            source = SourceDto(text, matched),
            results = results,
            message = message,
            labels = mapOf(
                "noResults" to context.getString(R.string.overlay_no_results),
                "addNote" to context.getString(R.string.overlay_add_note),
                "playAudio" to context.getString(R.string.overlay_play_audio),
                "copy" to context.getString(R.string.overlay_copy),
            ),
        ),
    )

    /** A kanji view; [kanji] without entries shows [message] instead. */
    fun kanji(context: Context, dark: Boolean, kanji: KanjiResult, message: String?): String = json.encodeToString(
        StateDto(
            theme = theme(dark),
            source = SourceDto(kanji.character, 1),
            kanji = kanji.takeIf { it.entries.isNotEmpty() },
            message = message.takeIf { kanji.entries.isEmpty() },
            labels = mapOf(
                "onyomi" to context.getString(R.string.overlay_kanji_onyomi),
                "kunyomi" to context.getString(R.string.overlay_kanji_kunyomi),
                "stat_strokes" to context.getString(R.string.overlay_kanji_strokes),
                "stat_grade" to context.getString(R.string.overlay_kanji_grade),
                "stat_jlpt" to context.getString(R.string.overlay_kanji_jlpt),
                "stat_freq" to context.getString(R.string.overlay_kanji_frequency),
            ),
        ),
    )

    /** Length of the first result's match in code points, for highlighting. */
    fun matchedLength(results: List<LookupResult>): Int =
        results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    /** Puts [text] on the clipboard; Android shows its own confirmation. */
    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.overlay_copy), text))
    }

    @Serializable
    private data class StateDto(
        val theme: String,
        val pending: Boolean = false,
        val engine: String = "",
        val source: SourceDto,
        val results: List<LookupResult> = emptyList(),
        val kanji: KanjiResult? = null,
        val message: String? = null,
        val labels: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class SourceDto(val text: String, val matched: Int)
}
