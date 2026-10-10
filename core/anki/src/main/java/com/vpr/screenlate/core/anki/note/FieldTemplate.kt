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

    /** The standard markers, this app's, and those of [language]; [transcriptions] keeps [PHONETIC_TRANSCRIPTIONS]. */
    fun markersFor(language: Language, transcriptions: Boolean = true): List<String> =
        MARKERS.filter { transcriptions || it != PHONETIC_TRANSCRIPTIONS } + APP_MARKERS + language.support.ankiMarkers

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
     * empty.
     */
    fun guess(modelName: String, fieldNames: List<String>): Map<String, String> {
        val preset = NoteTypePresets.find(modelName, fieldNames)
        return fieldNames.withIndex().associate { (index, field) ->
            field to (preset?.get(field.normalizedFieldName()) ?: guessField(field, index))
        }
    }

    /** Yomitan's `_getDefaultFieldValue` for one field. */
    fun guessField(fieldName: String, index: Int): String {
        if (index == 0) return "{expression}"
        val name = fieldName.normalizedFieldName()
        for (marker in MARKERS + APP_MARKERS + LANGUAGE_MARKERS) {
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
