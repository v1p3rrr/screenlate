package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.model.FrequencyGroup
import com.vpr.screenlate.dictionary.api.model.FrequencyValue
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.model.Transform
import kotlinx.serialization.json.JsonArray
import org.junit.Test

class YomitanSorterTest {
    private val options = LookupOptions(frequencyDictionary = "Freq", frequencyOrder = FrequencyOrder.ASCENDING)

    @Test
    fun longerMatchWins() {
        val short = result("食", matched = "食")
        val long = result("食べる", matched = "食べ")
        assertThat(sort(short, long)).containsExactly(long, short).inOrder()
    }

    @Test
    fun shorterDeinflectionChainWins() {
        val inflected = result("する", matched = "しない", trace = 1)
        val plain = result("市内", matched = "しない")
        assertThat(sort(inflected, plain)).containsExactly(plain, inflected).inOrder()
    }

    @Test
    fun rankFrequencyOrdersAscendingAndMissingFrequencyGoesLast() {
        val rare = result("a", frequency = 5000)
        val common = result("b", frequency = 12)
        val unknown = result("c")
        assertThat(sort(unknown, rare, common)).containsExactly(common, rare, unknown).inOrder()
    }

    @Test
    fun occurrenceFrequencyOrdersDescending() {
        val rare = result("a", frequency = 3)
        val common = result("b", frequency = 900)
        val sorted = YomitanSorter.sort(
            listOf(rare, common),
            options.copy(frequencyOrder = FrequencyOrder.DESCENDING),
            emptyList(),
        )
        assertThat(sorted).containsExactly(common, rare).inOrder()
    }

    @Test
    fun dictionaryPriorityBreaksFrequencyTies() {
        val second = result("a", dictionary = "Second", score = 10)
        val first = result("b", dictionary = "First")
        val sorted = YomitanSorter.sort(listOf(second, first), options, listOf("First", "Second"))
        assertThat(sorted).containsExactly(first, second).inOrder()
    }

    @Test
    fun primaryReadingComesFirst() {
        val other = result("日本", reading = "にっぽん", matched = "日本")
        val primary = result("日本", reading = "にほん", matched = "日")
        val sorted = YomitanSorter.sort(listOf(other, primary), options.copy(primaryReading = "にほん"), emptyList())
        assertThat(sorted.first()).isEqualTo(primary)
    }

    private fun sort(vararg results: LookupResult) = YomitanSorter.sort(results.toList(), options, listOf("Dict"))

    private fun result(
        expression: String,
        reading: String = expression,
        matched: String = expression,
        trace: Int = 0,
        frequency: Int? = null,
        dictionary: String = "Dict",
        score: Int = 0,
    ) = LookupResult(
        matched = matched,
        deinflected = if (trace > 0) expression else matched,
        trace = List(trace) { Transform("step$it") },
        term = TermEntry(
            expression = expression,
            reading = reading,
            score = score,
            glossaries = listOf(Glossary(dictionary, JsonArray(emptyList()))),
            frequencies = frequency?.let { listOf(FrequencyGroup("Freq", listOf(FrequencyValue(it)))) }.orEmpty(),
        ),
    )
}
