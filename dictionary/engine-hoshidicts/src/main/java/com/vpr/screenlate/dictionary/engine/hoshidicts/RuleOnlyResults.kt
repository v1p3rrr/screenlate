package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.FormOfTags
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

/**
 * The results without those found through a headword's spelling without marks ([LookupResult.respelled]) where another
 * result of the same matched text is found as written (`LanguageSupport.respelledOnlyWithoutEntry`): Welsh twr finds
 * twr and not tŵr, ty finds tŷ.
 */
internal fun List<LookupResult>.withoutRespelledBesideEntries(): List<LookupResult> {
    val written = filterNot { it.respelled }.mapTo(HashSet()) { it.matched }
    return filterNot { it.respelled && it.matched in written }
}

/**
 * The results with the steps named by form-of tags in [rules] (tag to rule id, `LanguageSupport.formTagRules`;
 * the tag cleaned of leftover gloss, [FormOfTags.clean]) named after those rules, each chain once. A word takes one
 * initial mutation, so a chain of two such steps is dropped (fara → bara by the rule, then bara → para by the
 * dictionary's row), and so is a result left without chains. Results whose headword is a single letter that only such
 * steps reach are left out too: Wiktionary's Welsh rows give b as the lemma of fara.
 */
internal fun List<LookupResult>.withFormTagsAsRules(rules: Map<String, String>): List<LookupResult> {
    if (rules.isEmpty()) return this
    val ids = rules.values.toSet()
    return mapNotNull { result ->
        val chains = (listOf(result.trace) + result.otherTraces)
            .map { chain ->
                chain.map { step -> rules[FormOfTags.clean(step.name)]?.let { step.copy(name = it) } ?: step }
            }
            .filter { chain -> chain.count { it.name in ids } <= 1 }
            .distinctBy { chain -> chain.map { it.name } }
        val expression = result.term.expression
        val letter = expression.codePointCount(0, expression.length) == 1
        when {
            chains.isEmpty() -> null
            letter && chains.all { chain -> chain.isNotEmpty() && chain.all { it.name in ids } } -> null
            else -> result.copy(trace = chains.first(), otherTraces = chains.drop(1))
        }
    }
}
