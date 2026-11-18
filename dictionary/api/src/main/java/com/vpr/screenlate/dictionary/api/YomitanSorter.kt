package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.FrequencyGroup
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlin.math.floor

/**
 * Orders lookup results the way Yomitan does:
 * primary reading, longer match, fewer text normalizations, spellings found as written before those found only
 * without their marks ([LookupResult.respelled], the native lookup's own key), shorter deinflection chain, term
 * equal to the deinflected text, sort frequency, dictionary priority, score, longer term, term text, and more
 * definitions.
 *
 * Engines may already sort by most of these; this adds dictionary priority and makes the order independent of
 * the engine.
 */
object YomitanSorter {
    private val LEADING_DIGITS = Regex("^\\d+")

    /**
     * @param dictionaryOrder term dictionary titles, highest priority first.
     * @param frequencyModes `"rank-based"` or `"occurrence-based"` by frequency dictionary title, used when sorting by
     *   [FrequencySort.HARMONIC_TITLE] or [FrequencySort.AVERAGE_TITLE] to exclude occurrence-based dictionaries.
     */
    fun sort(
        results: List<LookupResult>,
        options: LookupOptions,
        dictionaryOrder: List<String>,
        frequencyModes: Map<String, String> = emptyMap(),
    ): List<LookupResult> {
        val priority = dictionaryOrder.withIndex().associate { (index, title) -> title to index }
        val frequencyDictionary = options.frequencyDictionary.takeIf { options.frequencyOrder != FrequencyOrder.DISABLED }
        val aggregate = FrequencySort.isAggregateTitle(frequencyDictionary)
        val descending = !aggregate && options.frequencyOrder == FrequencyOrder.DESCENDING

        fun frequency(result: LookupResult): Int? {
            if (frequencyDictionary == null) return null
            if (frequencyDictionary == FrequencySort.HARMONIC_TITLE) {
                return harmonicRank(result.term.frequencies, frequencyModes)
            }
            if (frequencyDictionary == FrequencySort.AVERAGE_TITLE) {
                return averageRank(result.term.frequencies, frequencyModes)
            }
            val values = result.term.frequencies
                .filter { it.dictionary == frequencyDictionary }
                .flatMap { group -> group.values.map { it.value } }
                .filter { it >= 0 }
            return if (descending) values.maxOrNull() else values.minOrNull()
        }

        fun dictionaryIndex(result: LookupResult): Int =
            result.term.glossaries.minOfOrNull { priority[it.dictionary] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

        val comparator = compareByDescending<LookupResult> { options.primaryReading != null && it.term.reading == options.primaryReading }
            .thenByDescending { it.matched.codePointCount(0, it.matched.length) }
            .thenBy { it.preprocessorSteps }
            .thenBy { it.respelled }
            .thenBy { it.trace.size }
            .thenByDescending { options.language.support.preferDictionaryForms && it.trace.any { step -> step.form } }
            .thenByDescending { it.term.expression == it.deinflected }
            .thenComparator { a, b -> compareFrequency(frequency(a), frequency(b), descending) }
            .thenBy { dictionaryIndex(it) }
            .thenByDescending { it.term.score }
            .thenByDescending { it.term.expression.length }
            .thenBy { it.term.expression }
            .thenByDescending { it.term.glossaries.size }
        return results.sortedWith(comparator)
    }

    /** Harmonic mean of rank-based frequency numbers, matching `note.js` (`{frequency-harmonic-rank}`). */
    fun harmonicRank(frequencies: List<FrequencyGroup>, frequencyModes: Map<String, String> = emptyMap()): Int? {
        val numbers = rankNumbers(frequencies, frequencyModes)
        if (numbers.isEmpty()) return null
        val sum = numbers.sumOf { 1.0 / it }
        return if (sum > 0.0) floor(numbers.size / sum).toInt() else null
    }

    /** Arithmetic mean of rank-based frequency numbers, matching `note.js` (`{frequency-average-rank}`). */
    fun averageRank(frequencies: List<FrequencyGroup>, frequencyModes: Map<String, String> = emptyMap()): Int? {
        val numbers = rankNumbers(frequencies, frequencyModes)
        if (numbers.isEmpty()) return null
        return (numbers.sumOf { it.toLong() } / numbers.size).toInt()
    }

    private fun rankNumbers(frequencies: List<FrequencyGroup>, frequencyModes: Map<String, String>): List<Int> =
        frequencies.mapNotNull { group ->
            if (frequencyModes[group.dictionary] == "occurrence-based") return@mapNotNull null
            val first = group.values.firstOrNull() ?: return@mapNotNull null
            val parsed = LEADING_DIGITS.find(first.displayValue)?.value?.toIntOrNull() ?: 0
            when {
                parsed > 0 -> parsed
                first.value > 0 -> first.value
                else -> null
            }
        }

    /** Terms with a frequency come before terms without one. */
    private fun compareFrequency(a: Int?, b: Int?, descending: Boolean): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        descending -> b.compareTo(a)
        else -> a.compareTo(b)
    }
}
