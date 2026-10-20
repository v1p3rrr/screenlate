package com.vpr.screenlate.overlay.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
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
     * @param language the language of the lookup, for the labels of [kanji]'s readings.
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
        installation: Boolean = false,
        ocrModels: Boolean = false,
        language: Language,
    ): String = json.encodeToString(
        StateDto(
            theme = theme.css,
            pending = pending,
            noteWait = noteWait,
            engine = engine,
            ocrError = ocrError,
            hideSource = hideSource,
            source = SourceDto(text, matched, results.firstOrNull()?.lang ?: kanji?.lang),
            results = results,
            kanji = kanji,
            message = message.takeIf { kanji == null },
            labels = if (kanji != null) labels(context) + kanjiLabels(context, language) else labels(context),
            sentence = sentence,
            installation = installation,
            ocrModels = ocrModels,
            compound = compoundLine(context, results),
            rightToLeft = language.support.rightToLeft,
        ),
    )

    /** "Compound: Küchentisch + Lampenschirm" above the entries of a word found only as its parts; empty otherwise. */
    private fun compoundLine(context: Context, results: List<LookupResult>): String {
        val parts = results.firstOrNull()?.compound.orEmpty()
        return if (parts.size < 2) "" else context.getString(R.string.overlay_compound, parts.joinToString(" + "))
    }

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
        "transcriptionDictionaries" to context.getString(R.string.overlay_transcription_dictionaries),
        "showAll" to context.getString(R.string.overlay_show_all),
        "ocrError" to context.getString(R.string.overlay_ocr_error_title),
        "translate" to context.getString(R.string.overlay_translate),
        "translating" to context.getString(R.string.overlay_translating),
        "installation" to context.getString(R.string.installation_open),
        "ocrModels" to context.getString(R.string.overlay_open_ocr_models),
    )

    /** A kanji view; [kanji] without entries shows [message] instead. */
    fun kanji(context: Context, theme: PageTheme, kanji: KanjiResult, message: String?, language: Language): String =
        json.encodeToString(
            StateDto(
                theme = theme.css,
                source = SourceDto(kanji.character, 1, kanji.lang),
                kanji = kanji.takeIf { it.entries.isNotEmpty() },
                message = message.takeIf { kanji.entries.isEmpty() },
                labels = labels(context) + kanjiLabels(context, language),
                rightToLeft = language.support.rightToLeft,
            ),
        )

    private fun kanjiLabels(context: Context, language: Language): Map<String, String> = readingLabels(context, language) + mapOf(
        "stat_strokes" to context.getString(R.string.overlay_kanji_strokes),
        "stat_grade" to context.getString(R.string.overlay_kanji_grade),
        "stat_jlpt" to context.getString(R.string.overlay_kanji_jlpt),
        "stat_freq" to context.getString(R.string.overlay_kanji_frequency),
    )

    /**
     * A character dictionary's two reading fields: on and kun readings of kanji, pinyin of Chinese characters, the
     * Korean reading of Hanja.
     */
    private fun readingLabels(context: Context, language: Language): Map<String, String> = when (language) {
        Language.JAPANESE, Language.ENGLISH, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN, Language.MALAY, Language.FILIPINO, Language.VIETNAMESE, Language.RUSSIAN,
        Language.CROATIAN, Language.SERBIAN, Language.UKRAINIAN, Language.BELARUSIAN, Language.GREEK, Language.HINDI, Language.BENGALI, Language.GEORGIAN, Language.ARMENIAN, Language.THAI, Language.KHMER, Language.ARABIC, Language.PERSIAN, Language.HEBREW, Language.KAZAKH, Language.KYRGYZ -> mapOf(
            "onyomi" to context.getString(R.string.overlay_kanji_onyomi),
            "kunyomi" to context.getString(R.string.overlay_kanji_kunyomi),
        )
        Language.CHINESE -> context.getString(R.string.overlay_character_pinyin).let { mapOf("onyomi" to it, "kunyomi" to it) }
        Language.KOREAN ->
            context.getString(R.string.overlay_character_korean_reading).let { mapOf("onyomi" to it, "kunyomi" to it) }
    }

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
        val installation: Boolean = false,
        /** The message has a button to the language's on-device models. */
        val ocrModels: Boolean = false,
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
        /** The line above the entries of a compound's parts; empty for a word with its own entry. */
        val compound: String = "",
        /** The language is written right to left: the text, headwords and readings run that way in their places. */
        val rightToLeft: Boolean = false,
    )

    /** @property lang the script of [text] where the language has several, as the results' `lang`. */
    @Serializable
    private data class SourceDto(val text: String, val matched: Int, val lang: String? = null)
}
