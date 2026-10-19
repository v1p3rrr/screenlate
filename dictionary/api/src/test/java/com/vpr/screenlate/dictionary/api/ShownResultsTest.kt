package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.FrequencyGroup
import com.vpr.screenlate.dictionary.api.model.FrequencyValue
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.model.Transform
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import kotlinx.coroutines.test.runTest
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

        val merged = results.mergeSameWords(order, chinese)

        assertThat(merged.map { it.term.expression }).containsExactly("图书馆", "图书").inOrder()
        assertThat(merged[0].term.glossaries.map { it.dictionary }).containsExactly("CC-CEDICT", "Wiktionary").inOrder()
        assertThat(merged[0].term.frequencies).hasSize(1)
    }

    @Test
    fun `readings spaced differently are one word`() {
        val results = listOf(result("你好", "nǐhǎo", "CC-CEDICT"), result("你好", "nǐ hǎo", "Wiktionary"))

        val merged = results.mergeSameWords(order, chinese)

        assertThat(merged).hasSize(1)
        assertThat(merged[0].term.reading).isEqualTo("nǐhǎo")
        assertThat(merged[0].term.glossaries.map { it.dictionary }).containsExactly("CC-CEDICT", "Wiktionary").inOrder()
    }

    @Test
    fun `words of another reading stay apart`() {
        val results = listOf(result("行", "xíng", "CC-CEDICT"), result("行", "háng", "CC-CEDICT"))

        assertThat(results.mergeSameWords(order, chinese)).hasSize(2)
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
    fun `a word is spelled in the other script as the dictionaries list it with its reading`() = runTest {
        val listed = mapOf("乾" to "gān", "幹" to "gàn", "干" to "gān", "干涉" to "gān shè", "髮" to "fà", "頭髮" to "tóu fa")
        suspend fun spell(expression: String, reading: String, tag: String) = spelling(expression, reading, tag, chinese) {
            listOfNotNull(listed[it]?.let { reading -> result(it, reading, "CC-CEDICT") })
        }

        // 干 has several Traditional forms; the reading tells them apart.
        assertThat(spell("干", "gān", "zh-Hant")).isEqualTo("乾")
        assertThat(spell("干", "gàn", "zh-Hant")).isEqualTo("幹")
        // Written alike in both scripts: itself, as the dictionary lists it.
        assertThat(spell("干涉", "gānshè", "zh-Hant")).isEqualTo("干涉")
        assertThat(spell("头发", "tóufa", "zh-Hant")).isEqualTo("頭髮")
        // Nothing listed: the most likely form.
        assertThat(spell("发", "fā", "zh-Hant")).isEqualTo("發")
        assertThat(spell("發", "fā", "zh-Hans")).isEqualTo("发")
        // Already in the script, or the same in both.
        assertThat(spell("發", "fā", "zh-Hant")).isEqualTo("發")
        assertThat(spell("你好", "nǐhǎo", "zh-Hant")).isEqualTo("你好")
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

    @Test
    fun `chains through an ignored form tag are dropped and a word reached only through them is left out`() {
        val classifier = Transform("classifier")
        val tags = setOf("classifier")
        val noun = result("bàn", "", "Wiktionary", matched = "cái", trace = listOf(classifier))
        assertThat(noun.withoutChainsThrough(tags)).isNull()
        val altOf = listOf(Transform("alt-of"))
        val both = noun.copy(otherTraces = listOf(altOf))
        assertThat(both.withoutChainsThrough(tags)).isEqualTo(noun.copy(trace = altOf))
        val word = result("cái", "", "Wiktionary")
        assertThat(word.withoutChainsThrough(tags)).isSameInstanceAs(word)
        val hanNom = result("車", "", "Wiktionary", matched = "xa", trace = listOf(Transform("Hán-Nôm")))
        assertThat(hanNom.withoutChainsThrough(tags)).isSameInstanceAs(hanNom)
        assertThat(noun.withoutChainsThrough(emptySet())).isSameInstanceAs(noun)
        // Malay "baca" is found itself and, through its Jawi spelling, again (baca → باچ → baca).
        val jawi = Transform("Jawi")
        val baca = result("baca", "", "Wiktionary")
            .copy(otherTraces = listOf(listOf(jawi, jawi), listOf(Transform("alt-of"), jawi)))
        assertThat(baca.withoutChainsThrough(Language.MALAY.support.ignoredFormTags))
            .isEqualTo(baca.copy(otherTraces = emptyList()))
        // Other languages keep every form-of result.
        for (language in Language.entries - Language.VIETNAMESE) {
            assertThat(noun.withoutChainsThrough(language.support.ignoredFormTags)).isSameInstanceAs(noun)
        }
    }

    @Test
    fun `a spelling tag of another language's dictionary leaves the lemma in place`() {
        val found = result("圖書館", "", "Wiktionary", matched = "图书馆", trace = listOf(Transform("Simplified-Chinese")))
        for (language in Language.entries - Language.CHINESE) {
            val shown = found.asShown(language.support)
            assertThat(shown.term.expression).isEqualTo("圖書館")
            assertThat(shown.trace).containsExactly(Transform("Simplified-Chinese"))
        }
    }

    @Test
    fun `readings spaced differently stay apart outside Chinese`() {
        val results = listOf(result("日本語", "にほんご", "Jitendex"), result("日本語", "にほん ご", "Wiktionary"))
        assertThat(results.mergeSameWords(order, Language.JAPANESE.support)).hasSize(2)
    }

    @Test
    fun `a match that ends before a stress or tone mark is left out of a word lookup`() {
        fun shown(text: String, language: Language, vararg matched: String) =
            matched.map { result(it, "", "Wiktionary", matched = it) }
                .fromVariant(text, MappedText.identity(text), language.support)
                .map { it.matched }

        assertThat(shown("ко\u0301шка сидит", Language.RUSSIAN, "ко\u0301шка", "ко")).containsExactly("ко\u0301шка")
        assertThat(shown("hoa\u0300 bi\u0300nh", Language.VIETNAMESE, "hoa\u0300 bi\u0300nh", "hoa\u0300 bi", "hoa"))
            .containsExactly("hoa\u0300 bi\u0300nh")
        // A match up to a space, a precomposed letter or the end of the text stays.
        assertThat(shown("кошка сидит", Language.RUSSIAN, "кошка", "кошка сидит")).containsExactly("кошка", "кошка сидит")
        assertThat(shown("hòa bình", Language.VIETNAMESE, "hòa")).containsExactly("hòa")
        // Lookups by letters keep every match, as before.
        assertThat(shown("か\u3099き", Language.JAPANESE, "か")).containsExactly("か")
    }

    @Test
    fun `a match of a converted text points at the source characters`() {
        val variant = MappedText.identity("1人").replace(Regex("1"), "一")
        val shown = listOf(result("一人", "ひとり", "Jitendex", matched = "一人"))
            .fromVariant("1人", variant, Language.JAPANESE.support)
        assertThat(shown.single().matched).isEqualTo("1人")
        assertThat(shown.single().preprocessorSteps).isEqualTo(1)
    }
}
