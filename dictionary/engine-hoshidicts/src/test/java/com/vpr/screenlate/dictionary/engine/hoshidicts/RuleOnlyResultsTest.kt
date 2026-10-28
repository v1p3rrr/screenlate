package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.model.Transform
import org.junit.Test

class RuleOnlyResultsTest {
    private val rules = setOf("-ul", "agreement")

    private fun result(matched: String, expression: String, vararg chains: List<String>) = LookupResult(
        matched = matched,
        deinflected = expression,
        trace = chains.firstOrNull().orEmpty().map { Transform(it) },
        otherTraces = chains.drop(1).map { chain -> chain.map { Transform(it) } },
        term = TermEntry(expression, expression),
    )

    @Test
    fun `a rule's result is left out where the text has its own entry`() {
        val casa = result("casă", "casă")
        val cas = result("casă", "cas", listOf("agreement"))
        assertThat(listOf(casa, cas).withoutRuleOnlyResults(rules)).containsExactly(casa)
    }

    @Test
    fun `a rule's result stays where nothing else matches as much`() {
        val consiliu = result("Consiliul", "consiliu", listOf("-ul"))
        val shorter = result("Consiliu", "consiliu")
        assertThat(listOf(consiliu, shorter).withoutRuleOnlyResults(rules)).containsExactly(consiliu, shorter).inOrder()
    }

    @Test
    fun `form-of chains and results with a chain free of rules count as entries`() {
        val form = result("aflată", "afla", listOf("feminine singular"))
        val both = result("aflată", "aflat", listOf("agreement"), listOf("feminine"))
        val rule = result("aflată", "afl", listOf("agreement"))
        val shown = listOf(form, both, rule).withoutRuleOnlyResults(rules)
        assertThat(shown.map { it.term.expression }).containsExactly("afla", "aflat").inOrder()
        assertThat(shown.first()).isSameInstanceAs(form)
    }

    @Test
    fun `a result reached both ways keeps the chains free of rules`() {
        val house = result("taloon", "talo", listOf("-on"), listOf("illative"), listOf("-on", "plural"))
        val shown = listOf(house).withoutRuleOnlyResults(setOf("-on")).single()
        assertThat(shown.trace.map { it.name }).containsExactly("illative")
        assertThat(shown.otherTraces).isEmpty()
        val consiliu = result("Consiliul", "consiliu", listOf("-ul"), listOf("-ul", "plural"))
        assertThat(listOf(consiliu).withoutRuleOnlyResults(rules).single()).isSameInstanceAs(consiliu)
    }

    @Test
    fun `nothing is left out without rules`() {
        val list = listOf(result("casă", "casă"), result("casă", "cas", listOf("agreement")))
        assertThat(list.withoutRuleOnlyResults(emptySet())).isSameInstanceAs(list)
        assertThat(list.withoutRuleChainsBesideFreeOnes(emptySet())).isSameInstanceAs(list)
    }

    @Test
    fun `beside a form row only the rule chains go, and the rules' own results stay`() {
        val six = result("kuusi", "kuusi")
        val moon = result("kuusi", "kuu", listOf("-si"))
        val house = result("taloon", "talo", listOf("-on"), listOf("illative"))
        val shown = listOf(six, moon, house).withoutRuleChainsBesideFreeOnes(setOf("-si", "-on"))
        assertThat(shown.map { it.term.expression }).containsExactly("kuusi", "kuu", "talo").inOrder()
        assertThat(shown[0]).isSameInstanceAs(six)
        assertThat(shown[1]).isSameInstanceAs(moon)
        assertThat(shown[2].trace.map { it.name }).containsExactly("illative")
        assertThat(shown[2].otherTraces).isEmpty()
    }

    @Test
    fun `a respelled headword is left out where the text finds one as written`() {
        val twr = result("twr", "twr")
        val tower = result("twr", "tŵr").copy(respelled = true)
        val house = result("ty", "tŷ").copy(respelled = true)
        assertThat(listOf(twr, tower, house).withoutRespelledBesideEntries()).containsExactly(twr, house).inOrder()
    }

    private val welsh = mapOf("soft" to "soft mutation", "lenition" to "soft mutation", "soft mutation" to "soft mutation")

    @Test
    fun `form-of tags take the rule's name and a chain shows once`() {
        val bara = result("fara", "bara", listOf("soft mutation"), listOf("soft"), listOf("lenition"))
        val shown = listOf(bara).withFormTagsAsRules(welsh).single()
        assertThat(shown.trace.map { it.name }).containsExactly("soft mutation")
        assertThat(shown.otherTraces).isEmpty()
    }

    @Test
    fun `other tags and chains stay`() {
        val form = result("ferch", "merch", listOf("soft"), listOf("plural"))
        val shown = listOf(form).withFormTagsAsRules(welsh).single()
        assertThat(shown.trace.map { it.name }).containsExactly("soft mutation")
        assertThat(shown.otherTraces.map { chain -> chain.map { it.name } }).containsExactly(listOf("plural"))
    }

    @Test
    fun `a chain of two mutations is dropped, and a result left without chains`() {
        val para = result("fara", "para", listOf("soft mutation", "soft"))
        val both = result("ddwr", "twr", listOf("soft mutation", "soft"), listOf("plural"))
        val bara = result("fara", "bara", listOf("soft mutation"))
        val shown = listOf(para, both, bara).withFormTagsAsRules(welsh)
        assertThat(shown.map { it.term.expression }).containsExactly("twr", "bara").inOrder()
        assertThat(shown.first().trace.map { it.name }).containsExactly("plural")
        assertThat(shown.first().otherTraces).isEmpty()
    }

    @Test
    fun `a single letter reached only through mutation rows is left out`() {
        val letter = result("fara", "b", listOf("soft"))
        val word = result("fara", "bara", listOf("soft"))
        val own = result("a", "a")
        val plural = result("au", "a", listOf("plural"))
        assertThat(listOf(letter, word, own, plural).withFormTagsAsRules(welsh).map { it.term.expression })
            .containsExactly("bara", "a", "a").inOrder()
    }

    @Test
    fun `results stay as they are without form tag rules`() {
        val list = listOf(result("fara", "b", listOf("soft")))
        assertThat(list.withFormTagsAsRules(emptyMap())).isSameInstanceAs(list)
    }
}
