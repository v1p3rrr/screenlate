package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.FormOfTags
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.Transform

/**
 * The results without those reached only through [rules] (rule ids) where another result of the same matched text
 * needs none of them: Romanian casă finds its own entry, and the agreement ending's cas is left out
 * (`LanguageSupport.inflectionRulesOnlyWithoutEntry`). A form-of entry's chain is not a rule's, also where its tag is
 * named as a rule ([Transform.form]: the dative rows of Wiktionary's Uzbek export). A result reached both ways keeps
 * only its chains free of them ([withoutRuleChainsBesideFreeOnes]).
 */
internal fun List<LookupResult>.withoutRuleOnlyResults(rules: Set<String>): List<LookupResult> {
    if (rules.isEmpty()) return this
    fun onlyThroughRules(result: LookupResult) = chains(result).all { it.throughAny(rules) }
    val plain = filterNot(::onlyThroughRules).mapTo(HashSet()) { it.matched }
    return filterNot { onlyThroughRules(it) && it.matched in plain }.withoutRuleChainsBesideFreeOnes(rules)
}

/**
 * The results with their chains through [rules] (rule ids) left out where a chain free of them reaches the same result,
 * while results only the rules reach stay (`LanguageSupport.ruleChainsOnlyWithoutRow`): Finnish taloon shows talo's
 * illative row, not also talo with -on, and kuusi still shows kuu with -si after its own entry.
 */
internal fun List<LookupResult>.withoutRuleChainsBesideFreeOnes(rules: Set<String>): List<LookupResult> {
    if (rules.isEmpty()) return this
    return map { result ->
        val all = chains(result)
        val free = all.filterNot { it.throughAny(rules) }
        if (free.isEmpty() || free.size == all.size) result else result.copy(trace = free.first(), otherTraces = free.drop(1))
    }
}

private fun chains(result: LookupResult) = listOf(result.trace) + result.otherTraces

private fun List<Transform>.throughAny(rules: Set<String>) = any { !it.form && it.name in rules }

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
