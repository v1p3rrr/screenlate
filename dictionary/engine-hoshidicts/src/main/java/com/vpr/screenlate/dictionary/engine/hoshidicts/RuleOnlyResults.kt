package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.model.LookupResult

/**
 * The results without those reached only through [rules] (rule ids) where another result of the same matched text
 * needs none of them: Romanian casă finds its own entry, and the agreement ending's cas is left out
 * (`LanguageSupport.inflectionRulesOnlyWithoutEntry`). A form-of entry's chain is not a rule's.
 */
internal fun List<LookupResult>.withoutRuleOnlyResults(rules: Set<String>): List<LookupResult> {
    if (rules.isEmpty()) return this
    fun throughRules(result: LookupResult) =
        (listOf(result.trace) + result.otherTraces).all { chain -> chain.any { it.name in rules } }
    val plain = filterNot(::throughRules).mapTo(HashSet()) { it.matched }
    return filterNot { it.matched in plain && throughRules(it) }
}
