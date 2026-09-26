package com.vpr.screenlate.dictionary.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One term found for a lookup string. Terms are grouped by expression and reading; [TermEntry.glossaries]
 * holds the definitions of every enabled dictionary in priority order.
 *
 * @property matched prefix of the lookup string that produced this term, as it appeared in the source text.
 * @property deinflected dictionary form the matched text was reduced to.
 * @property trace deinflection steps from [matched] to [deinflected], outermost first.
 * @property preprocessorSteps number of text normalizations applied (kana conversion, width, etc.).
 */
@Serializable
data class LookupResult(
    val matched: String,
    val deinflected: String,
    val trace: List<Transform> = emptyList(),
    val term: TermEntry,
    val preprocessorSteps: Int = 0,
)

@Serializable
data class Transform(
    val name: String,
    val description: String = "",
)

/** @property rules part-of-speech rules of the term, space-separated (Yomitan `rules`). */
@Serializable
data class TermEntry(
    val expression: String,
    val reading: String,
    val rules: String = "",
    val score: Int = 0,
    val glossaries: List<Glossary> = emptyList(),
    val frequencies: List<FrequencyGroup> = emptyList(),
    val pitches: List<PitchGroup> = emptyList(),
)

/**
 * Definitions of one dictionary for a term.
 *
 * @property content Yomitan glossary array: strings, structured content or image objects.
 */
@Serializable
data class Glossary(
    val dictionary: String,
    val content: JsonElement,
    val definitionTags: String = "",
    val termTags: String = "",
)

@Serializable
data class FrequencyGroup(
    val dictionary: String,
    val values: List<FrequencyValue> = emptyList(),
)

@Serializable
data class FrequencyValue(
    val value: Int,
    val displayValue: String = "",
)

@Serializable
data class PitchGroup(
    val dictionary: String,
    val pitches: List<PitchAccent> = emptyList(),
    val transcriptions: List<String> = emptyList(),
)

/**
 * @property position downstep position in morae; 0 is heiban.
 * @property pattern explicit high/low pattern (`HLL`) when the dictionary provides one.
 */
@Serializable
data class PitchAccent(
    val position: Int,
    val pattern: String = "",
    val nasal: List<Int> = emptyList(),
    val devoice: List<Int> = emptyList(),
)

@Serializable
data class DictionaryStyle(
    val dictionary: String,
    val css: String,
)

@Serializable
data class KanjiResult(
    val character: String,
    val entries: List<KanjiEntry> = emptyList(),
)

@Serializable
data class KanjiEntry(
    val dictionary: String,
    val onyomi: String = "",
    val kunyomi: String = "",
    val tags: String = "",
    val definitions: List<String> = emptyList(),
    val stats: Map<String, String> = emptyMap(),
)
