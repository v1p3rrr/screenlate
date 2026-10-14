package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.FrequencyGroup
import com.vpr.screenlate.dictionary.api.model.FrequencyValue
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.model.Transform
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import org.junit.Test

class ShownResultsTest {
    private val chinese = Language.CHINESE.support
    private val order = listOf("CC-CEDICT", "Wiktionary", "Frequency")

    private fun result(
        expression: String,
        reading: String,
        dictionary: String,
        matched: String = expression,
        trace: List<Transform> = emptyList(),
        frequency: Int? = null,
    ) = LookupResult(
        matched = matched,
        deinflected = expression,
        trace = trace,
        term = TermEntry(
            expression,
            reading,
            glossaries = listOf(Glossary(dictionary, "[\"$dictionary $expression\"]")),
            frequencies = listOfNotNull(frequency?.let { FrequencyGroup("Frequency", listOf(FrequencyValue(it))) }),
        ),
    )

    @Test
    fun `a word found through its simplified spelling shows the spelling that was read`() {
        val found = result("圖書館", "túshūguǎn", "Wiktionary", matched = "图书馆", trace = listOf(Transform("Simplified-Chinese")))

        val shown = found.asShown(chinese)

        assertThat(shown.term.expression).isEqualTo("图书馆")
        assertThat(shown.deinflected).isEqualTo("图书馆")
        assertThat(shown.trace).isEmpty()
    }

    @Test
    fun `other form-of words keep their lemma and the reading loses its numbered pinyin`() {
        val found = result("走", "zǒu (zou³)", "Wiktionary", matched = "行", trace = listOf(Transform("alt-of")))

        val shown = found.asShown(chinese)

        assertThat(shown.term.expression).isEqualTo("走")
        assertThat(shown.term.reading).isEqualTo("zǒu")
        assertThat(shown.trace).containsExactly(Transform("alt-of"))
    }

    @Test
    fun `the same word from several dictionaries becomes one entry in the first one's place`() {
        val results = listOf(
            result("图书馆", "túshūguǎn", "Wiktionary"),
            result("图书", "túshū", "CC-CEDICT"),
            result("图书馆", "túshūguǎn", "CC-CEDICT", frequency = 3000),
        )

        val merged = results.mergeSameWords(order)

        assertThat(merged.map { it.term.expression }).containsExactly("图书馆", "图书").inOrder()
        assertThat(merged[0].term.glossaries.map { it.dictionary }).containsExactly("CC-CEDICT", "Wiktionary").inOrder()
        assertThat(merged[0].term.frequencies).hasSize(1)
    }

    @Test
    fun `words of another reading stay apart`() {
        val results = listOf(result("行", "xíng", "CC-CEDICT"), result("行", "háng", "CC-CEDICT"))

        assertThat(results.mergeSameWords(order)).hasSize(2)
    }

    @Test
    fun `with one script fixed a word gives way to its spelling in that script`() {
        val results = listOf(
            result("头发", "tóufa", "CC-CEDICT", frequency = 2000),
            result("头", "tóu", "CC-CEDICT"),
            result("頭髮", "tóufa", "CC-CEDICT", matched = "頭髮"),
        )

        val shown = results.inScript("zh-Hant", chinese, order)

        assertThat(shown.map { it.term.expression }).containsExactly("頭髮", "头").inOrder()
        assertThat(shown[0].term.frequencies).hasSize(1)
    }

    @Test
    fun `a word no dictionary spells in the fixed script stays`() {
        val results = listOf(result("头发", "tóufa", "CC-CEDICT"))

        assertThat(results.inScript("zh-Hant", chinese, order).map { it.term.expression }).containsExactly("头发")
    }

    @Test
    fun `a word takes its own script, then the text's, then the setting's`() {
        val settings = LookupSettings(script = "zh-Hans")
        assertThat(scriptTag("電腦", "zh-Hans", settings, chinese)).isEqualTo("zh-Hant")
        assertThat(scriptTag("中文", "zh-Hant", settings, chinese)).isEqualTo("zh-Hant")
        assertThat(scriptTag("中文", null, settings, chinese)).isEqualTo("zh-Hans")
        assertThat(scriptTag("電腦", null, settings.copy(fixedScript = true), chinese)).isEqualTo("zh-Hans")
        assertThat(scriptTag("water", null, LookupSettings(), Language.ENGLISH.support)).isNull()
    }
}
