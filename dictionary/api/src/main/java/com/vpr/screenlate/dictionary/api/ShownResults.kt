package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry

/**
 * The result as the language shows it: the reading without notations a dictionary adds, and a word reached through a
 * form-of entry that only spells it in another script (`LanguageSupport.spellingFormTags`) under the spelling that was
 * read. Applies before [LookupResult.inSource], while [LookupResult.matched] is still the searched text.
 */
internal fun LookupResult.asShown(support: LanguageSupport): LookupResult {
    val reading = support.displayReading(term.reading)
    val respelled = trace.size == 1 && trace[0].name in support.spellingFormTags
    if (!respelled) return if (reading == term.reading) this else copy(term = term.copy(reading = reading))
    return copy(
        deinflected = matched,
        trace = emptyList(),
        otherTraces = emptyList(),
        term = term.copy(expression = matched, reading = reading),
    )
}

/**
 * Results of the same word (expression and reading, see [LanguageSupport.normalizeReading]) as one entry in the place
 * of the first: a respelled word ([asShown]) meets the dictionaries that list its spelling itself, spellings of the
 * text may find a word twice, and dictionaries space readings differently. Definitions, frequencies and
 * pronunciations of the later ones join it unless it has them already.
 */
internal fun List<LookupResult>.mergeSameWords(dictionaryOrder: List<String>, support: LanguageSupport): List<LookupResult> {
    val merged = LinkedHashMap<Pair<String, String>, LookupResult>()
    for (result in this) {
        val key = result.term.expression to support.normalizeReading(result.term.reading)
        merged[key] = merged[key]?.joined(result.term, dictionaryOrder) ?: result
    }
    return merged.values.toList()
}

/**
 * With [LookupSettings.fixedScript][com.vpr.screenlate.dictionary.api.settings.LookupSettings.fixedScript]: a word
 * written in another script than [tag] gives way to the same word in [tag] where the lookup found that too (the text in
 * [tag] is looked up as well), which takes its place and its definitions. Words no dictionary spells in [tag] stay.
 */
internal fun List<LookupResult>.inScript(
    tag: String,
    support: LanguageSupport,
    dictionaryOrder: List<String>,
): List<LookupResult> {
    fun counterpart(result: LookupResult): LookupResult? {
        val script = support.scriptOf(result.term.expression)
        if (script == null || script == tag) return null
        val spellings = support.inScript(MappedText.identity(result.term.expression), tag).map { it.text }.toSet()
        val reading = support.normalizeReading(result.term.reading)
        return firstOrNull { it.term.expression in spellings && support.normalizeReading(it.term.reading) == reading }
    }
    val counterparts = associateWith(::counterpart)
    // Keyed by the shown word; it takes the first place of itself and the words it replaces.
    val shown = LinkedHashMap<LookupResult, LookupResult>()
    for (result in this) {
        val target = counterparts[result] ?: result
        val current = shown[target]
        shown[target] = when {
            current == null -> if (target === result) result else target.joined(result.term, dictionaryOrder)
            target === result -> current
            else -> current.joined(result.term, dictionaryOrder)
        }
    }
    return shown.values.toList()
}

/** This result with [other]'s definitions, frequencies and pronunciations that it lacks. */
private fun LookupResult.joined(other: TermEntry, dictionaryOrder: List<String>): LookupResult {
    val glossaries = other.glossaries.filter { it !in term.glossaries }
    val frequencies = other.frequencies.filter { new -> term.frequencies.none { it.dictionary == new.dictionary } }
    val pitches = other.pitches.filter { new -> term.pitches.none { it.dictionary == new.dictionary } }
    if (glossaries.isEmpty() && frequencies.isEmpty() && pitches.isEmpty()) return this
    val rank = dictionaryOrder.withIndex().associate { it.value to it.index }
    return copy(
        term = term.copy(
            glossaries = (term.glossaries + glossaries).sortedBy { rank[it.dictionary] ?: Int.MAX_VALUE },
            frequencies = term.frequencies + frequencies,
            pitches = term.pitches + pitches,
        ),
    )
}
