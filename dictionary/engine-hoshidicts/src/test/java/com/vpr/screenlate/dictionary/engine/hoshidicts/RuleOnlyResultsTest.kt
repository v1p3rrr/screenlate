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
        assertThat(listOf(form, both, rule).withoutRuleOnlyResults(rules)).containsExactly(form, both).inOrder()
    }

    @Test
    fun `nothing is left out without rules`() {
        val list = listOf(result("casă", "casă"), result("casă", "cas", listOf("agreement")))
        assertThat(list.withoutRuleOnlyResults(emptySet())).isSameInstanceAs(list)
    }
}
