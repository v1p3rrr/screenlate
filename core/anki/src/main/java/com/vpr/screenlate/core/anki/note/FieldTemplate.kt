package com.vpr.screenlate.core.anki.note

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support

/**
 * Field templates with `{marker}` placeholders, using Yomitan's marker names and output formats, so settings and
 * note types made for Yomitan work unchanged.
 *
 * Unknown markers are left in place so a typo is visible on the card instead of silently producing an empty
 * field.
 */
object FieldTemplate {
    // Android's ICU regex needs the closing brace escaped, unlike the JVM's.
    private val MARKER = Regex("""\{([\p{L}\p{N}_-]+)\}""")

    /** Yomitan's standard term markers, in its order. Markers without a source here render empty. */
    val MARKERS = listOf(
        "audio",
        "clipboard-image",
        "clipboard-text",
        "cloze-body",
        "cloze-prefix",
        "cloze-suffix",
        "conjugation",
        "dictionary",
        "dictionary-alias",
        "document-title",
        "expression",
        "frequencies",
        "frequency-harmonic-rank",
        "frequency-harmonic-occurrence",
        "frequency-average-rank",
        "frequency-average-occurrence",
        "furigana",
        "furigana-plain",
        "glossary",
        "glossary-brief",
        "glossary-no-dictionary",
        "glossary-plain",
        "glossary-plain-no-dictionary",
        "glossary-first",
        "glossary-first-brief",
        "glossary-first-no-dictionary",
        "part-of-speech",
        "phonetic-transcriptions",
        "reading",
        "screenshot",
        "search-query",
        "popup-selection-text",
        "sentence",
        "sentence-furigana",
        "sentence-furigana-plain",
        "tags",
        "url",
        "url-plain",
    )

    /** The sentence's machine translation, when one came; Yomitan has no such marker. */
    const val SENTENCE_TRANSLATION = "sentence-translation"

    /** Markers of this app beyond Yomitan's. */
    val APP_MARKERS = listOf(SENTENCE_TRANSLATION)

    /** Markers that exist only for some languages (Yomitan offers pitch accents for Japanese only). */
    private val LANGUAGE_MARKERS = Language.entries.flatMap { it.support.ankiMarkers }.distinct()

    /** Pronunciation in the phonetic alphabet; offered only while a pronunciation dictionary is on. */
    const val PHONETIC_TRANSCRIPTIONS = "phonetic-transcriptions"

    /** Yomitan's furigana markers; offered only where `LanguageSupport.furiganaMarkers`, but filled everywhere. */
    val FURIGANA_MARKERS = listOf("furigana", "furigana-plain", "sentence-furigana", "sentence-furigana-plain")

    /**
     * The standard markers, this app's, and those of [language]; [transcriptions] keeps [PHONETIC_TRANSCRIPTIONS]. The
     * furigana markers only for a language whose readings they show ([FURIGANA_MARKERS]).
     */
    fun markersFor(language: Language, transcriptions: Boolean = true): List<String> {
        val furigana = language.support.furiganaMarkers
        return MARKERS.filter { marker ->
            (transcriptions || marker != PHONETIC_TRANSCRIPTIONS) && (furigana || marker !in FURIGANA_MARKERS)
        } + APP_MARKERS + language.support.ankiMarkers
    }

    const val SINGLE_GLOSSARY_PREFIX = "single-glossary-"
    const val SINGLE_FREQUENCY_NUMBER_PREFIX = "single-frequency-number-"
    const val SINGLE_FREQUENCY_PREFIX = "single-frequency-"

    /** Per-dictionary markers of Screenlate versions before the Yomitan names; still rendered. */
    const val LEGACY_GLOSSARY_PREFIX = "glossary-"

    fun render(template: String, values: Map<String, String>): String =
        MARKER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }

    fun markersIn(template: String): Set<String> = MARKER.findAll(template).map { it.groupValues[1] }.toSet()

    /** Whether [marker] is one this app fills (possibly with an empty value), as opposed to a typo. */
    fun isKnown(marker: String): Boolean =
        marker in MARKERS ||
            marker in APP_MARKERS ||
            marker in LANGUAGE_MARKERS ||
            marker.startsWith(SINGLE_GLOSSARY_PREFIX) ||
            marker.startsWith(SINGLE_FREQUENCY_PREFIX) ||
            marker.startsWith(LEGACY_GLOSSARY_PREFIX)

    /** Yomitan's getKebabCase: the dictionary part of `single-glossary-*` and `single-frequency-*` markers. */
    fun kebab(title: String): String = title
        .replace(Regex("""[\s_　]"""), "-")
        .replace(Regex("""[^\p{L}\p{N}-]"""), "")
        .replace(Regex("-{2,}"), "-")
        .trim('-')
        .lowercase()

    /** `single-glossary-<dictionary>`: that dictionary's definitions only. */
    fun singleGlossaryMarker(dictionaryTitle: String): String = SINGLE_GLOSSARY_PREFIX + kebab(dictionaryTitle)

    /** `single-frequency-number-<dictionary>`: that frequency dictionary's first number. */
    fun singleFrequencyNumberMarker(dictionaryTitle: String): String =
        SINGLE_FREQUENCY_NUMBER_PREFIX + kebab(dictionaryTitle)

    /**
     * Templates for every field of a note type: a preset for well-known note types, otherwise Yomitan's rule — a
     * field whose whole name is a marker or one of its aliases gets that marker, the first field gets
     * `{expression}`, anything else stays empty. Matching whole names keeps flag fields such as `IsSentenceCard`
     * empty. Field names of note types made for [language] come first (`LanguageSupport.ankiFieldNames`).
     */
    fun guess(modelName: String, fieldNames: List<String>, language: Language): Map<String, String> {
        val preset = NoteTypePresets.find(modelName, fieldNames)
        return fieldNames.withIndex().associate { (index, field) ->
            val template = preset?.get(field.normalizedFieldName()) ?: guessField(field, index, language)
            field to if (index == 0) template else rightToLeft(template, language)
        }
    }

    /**
     * [template] with the markers of [language]'s right-to-left text (`LanguageSupport.rightToLeftMarkers`) in
     * `<span dir="rtl">`, so that a card laid out left to right shows an Arabic sentence in order. Markers with only
     * markup between them share one span (`{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}`), unless it would cut a tag
     * apart; a template that sets a direction already is left as it is. The first field of a note type gets none, as
     * AnkiDroid's duplicate check compares its markup.
     */
    fun rightToLeft(template: String, language: Language): String {
        val markers = language.support.rightToLeftMarkers
        if (markers.isEmpty() || DIRECTION.containsMatchIn(template)) return template
        val found = MARKER.findAll(template).filter { it.groupValues[1] in markers }.toList()
        if (found.isEmpty()) return template
        // Runs of such markers with only tags between them.
        val runs = mutableListOf(mutableListOf(found.first()))
        for (match in found.drop(1)) {
            val between = template.substring(runs.last().last().range.last + 1, match.range.first)
            if (TAGS_ONLY.matches(between)) runs.last() += match else runs += mutableListOf(match)
        }
        val ranges = runs.flatMap { run ->
            val range = run.first().range.first..run.last().range.last
            if (balanced(template.substring(range.first, range.last + 1))) listOf(range) else run.map { it.range }
        }
        val result = StringBuilder(template)
        for (range in ranges.asReversed()) {
            result.insert(range.last + 1, "</span>")
            result.insert(range.first, RIGHT_TO_LEFT_SPAN)
        }
        return result.toString()
    }

    /**
     * [template] with [marker] added at its end, as the marker picker adds it: a marker of [language]'s right-to-left
     * text in `<span dir="rtl">`, or into the span the template ends with ([rightToLeft]); none in the first field.
     */
    fun withMarker(template: String, marker: String, language: Language, firstField: Boolean): String {
        val placeholder = "{$marker}"
        if (firstField || marker !in language.support.rightToLeftMarkers) return template + placeholder
        val open = template.lastIndexOf(RIGHT_TO_LEFT_SPAN)
        if (open >= 0 && template.endsWith("</span>") && balanced(template.substring(open + RIGHT_TO_LEFT_SPAN.length, template.length - 7))) {
            return template.dropLast(7) + placeholder + "</span>"
        }
        return "$template$RIGHT_TO_LEFT_SPAN$placeholder</span>"
    }

    private const val RIGHT_TO_LEFT_SPAN = """<span dir="rtl">"""
    private val DIRECTION = Regex("""\bdir\s*=""", RegexOption.IGNORE_CASE)
    private val TAGS_ONLY = Regex("""(?:\s*<[^<>]*>)*\s*""")
    private val TAG = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)[^<>]*?(/?)>""")
    private val VOID_TAGS = setOf("br", "img", "hr", "wbr")

    /** Whether every tag [html] opens it also closes, after it. */
    private fun balanced(html: String): Boolean {
        val open = ArrayDeque<String>()
        for (tag in TAG.findAll(html)) {
            val (closing, name, selfClosing) = tag.destructured
            val lower = name.lowercase()
            when {
                selfClosing.isNotEmpty() || lower in VOID_TAGS -> Unit
                closing.isEmpty() -> open.addLast(lower)
                open.lastOrNull() == lower -> open.removeLast()
                else -> return false
            }
        }
        return open.isEmpty()
    }

    /**
     * Yomitan's `_getDefaultFieldValue` for one field, with [language]'s own field names first. The first field gets
     * `{expression}` unless its name asks for the word in one script (Traditional).
     */
    fun guessField(fieldName: String, index: Int, language: Language): String {
        val name = fieldName.normalizedFieldName()
        val support = language.support
        val own = support.ankiFieldNames.entries
            .firstOrNull { (_, names) -> names.any { it.normalizedFieldName() == name } }?.key
        if (index == 0) return "{${own?.takeIf { it in support.scriptMarkers } ?: "expression"}}"
        if (own != null) return "{$own}"
        for (marker in markersFor(language)) {
            val names = listOf(marker) + ALIASES[marker].orEmpty()
            if (names.any { it.normalizedFieldName() == name }) return "{$marker}"
        }
        return ""
    }

    private val ALIASES = mapOf(
        "expression" to listOf("phrase", "term", "word"),
        "reading" to listOf("expression-reading", "term-reading", "word-reading"),
        "furigana" to listOf("expression-furigana", "term-furigana", "word-furigana"),
        "glossary" to listOf("definition", "meaning"),
        "glossary-first" to listOf("primary-definition", "main-definition"),
        "audio" to listOf("sound", "word-audio", "term-audio", "expression-audio"),
        "dictionary" to listOf("dict"),
        "pitch-accents" to listOf("pitch", "pitch-accent", "pitch-pattern"),
        "sentence" to listOf("example-sentence"),
        "frequency-harmonic-rank" to listOf("freq", "frequency", "freq-sort", "freqency-sort", "frequency-sort"),
        "screenshot" to listOf("picture", "image"),
        "popup-selection-text" to listOf("selection", "selection-text"),
        "pitch-accent-positions" to listOf("pitch-position"),
        "pitch-accent-categories" to listOf("pitch-categories"),
        "pitch-accent-graphs" to listOf("graph", "pitch-graph", "pitch-graphs"),
        // Only names with "sentence": a bare "Translation" may mean the word's, and "Meaning" is the glossary.
        SENTENCE_TRANSLATION to listOf("sentence-meaning", "sentence-english", "sentence-eng"),
    )
}

/** Field names compare ignoring case and the separators `-`, `_` and space (Yomitan: `[-_ ]*`). */
internal fun String.normalizedFieldName(): String = lowercase().replace(Regex("[-_ ]"), "")
