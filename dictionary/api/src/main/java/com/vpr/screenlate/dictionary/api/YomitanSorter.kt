package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.dictionary.api.model.LookupResult

/**
 * Orders lookup results the way Yomitan does:
 * primary reading, longer match, fewer text normalizations, shorter deinflection chain, term equal to the
 * deinflected text, sort frequency, dictionary priority, score, and finally terms written in kana.
 *
 * Engines may already sort by most of these; this adds dictionary priority and makes the order independent of
 * the engine.
 */
object YomitanSorter {
    /**
     * @param dictionaryOrder term dictionary titles, highest priority first.
     */
    fun sort(
        results: List<LookupResult>,
        options: LookupOptions,
        dictionaryOrder: List<String>,
    ): List<LookupResult> {
        val priority = dictionaryOrder.withIndex().associate { (index, title) -> title to index }
        val frequencyDictionary = options.frequencyDictionary.takeIf { options.frequencyOrder != FrequencyOrder.DISABLED }
        val descending = options.frequencyOrder == FrequencyOrder.DESCENDING

        fun frequency(result: LookupResult): Int? {
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
            .thenBy { it.trace.size }
            .thenByDescending { it.term.expression == it.deinflected }
            .thenComparator { a, b -> compareFrequency(frequency(a), frequency(b), descending) }
            .thenBy { dictionaryIndex(it) }
            .thenByDescending { it.term.score }
            .thenByDescending { it.term.expression == it.term.reading }
        return results.sortedWith(comparator)
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
