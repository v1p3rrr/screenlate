package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.core.common.language.SearchResolution
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
 * The engine's results for [variant] of [source] as the lookup shows them ([asShown], [LookupResult.inSource]). In a
 * language looked up by words, a match that ends before a combining mark is left out: Yomitan's word resolution takes
 * a stress or tone mark typed apart for the end of a word, which would look up ко in ко́шка and hoa in hoà.
 */
internal fun List<LookupResult>.fromVariant(source: String, variant: MappedText, support: LanguageSupport): List<LookupResult> =
    filterNot { support.searchResolution == SearchResolution.WORD && it.endsBeforeMark(variant.text) }
        .map { it.asShown(support).inSource(source, variant) }

/** Whether a combining mark of [text] follows the match. */
private fun LookupResult.endsBeforeMark(text: String): Boolean {
    if (matched.length >= text.length) return false
    return when (Character.getType(text.codePointAt(matched.length))) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}

/**
 * The result without its chains of steps that go through a form-of entry with one of [tags]
 * (`LanguageSupport.ignoredFormTags`); null when every chain does, so the result is left out.
 */
internal fun LookupResult.withoutChainsThrough(tags: Set<String>): LookupResult? {
    if (tags.isEmpty()) return this
    val chains = listOf(trace) + otherTraces
    val kept = chains.filterNot { chain -> chain.any { it.name in tags } }
    return when {
        kept.size == chains.size -> this
        kept.isEmpty() -> null
        else -> copy(trace = kept.first(), otherTraces = kept.drop(1))
    }
}

/**
 * The result as [support] shows it: null for a headword it leaves out ([LanguageSupport.showsHeadword]), else
 * [withoutChainsThrough] its ignored form tags.
 */
internal fun LookupResult.shownBy(support: LanguageSupport): LookupResult? =
    takeIf { support.showsHeadword(term.expression) }?.withoutChainsThrough(support.ignoredFormTags)

internal fun TermEntry.isReadingless(): Boolean {
    if (reading.isEmpty()) return true
    if (reading == expression) {
        return expression.codePoints().anyMatch(Character::isIdeographic)
    }
    return false
}

internal fun LookupResult.isReadingless(): Boolean = term.isReadingless()

/**
 * Results of the same word (expression and reading, see [LanguageSupport.normalizeReading]) as one entry in the place
 * of the first: a respelled word ([asShown]) meets the dictionaries that list its spelling itself, spellings of the
 * text may find a word twice, and dictionaries space readings differently. Definitions, frequencies and
 * pronunciations of the later ones join it unless it has them already.
 */
internal fun List<LookupResult>.mergeSameWords(dictionaryOrder: List<String>, support: LanguageSupport): List<LookupResult> {
    val unambiguousReadings = groupBy { it.term.expression }
        .mapValues { (_, results) ->
            results.filterNot { it.isReadingless() }
                .map { support.normalizeReading(it.term.reading) }
                .distinct()
        }
        .filterValues { it.size == 1 }
        .mapValues { it.value.first() }

    val merged = LinkedHashMap<Pair<String, String>, LookupResult>()
    for (result in this) {
        val reading = if (!result.isReadingless()) {
            support.normalizeReading(result.term.reading)
        } else {
            unambiguousReadings[result.term.expression] ?: support.normalizeReading(result.term.reading)
        }
        val key = result.term.expression to reading
        val existing = merged[key]
        merged[key] = if (existing == null) {
            if (result.isReadingless()) {
                unambiguousReadings[result.term.expression]?.let { targetReading ->
                    result.copy(term = result.term.copy(reading = targetReading))
                } ?: result
            } else {
                result
            }
        } else {
            existing.joined(result.term, dictionaryOrder)
        }
    }
    return merged.values.toList()
}

/**
 * Merges entries with the same matched prefix, normalized reading and identical primary-dictionary glossary content
 * into one entry (Yomitan's `merge` grouping mode). The highest-ranked entry stays primary; distinct expressions of
 * merged entries are listed in [TermEntry.alternativeExpressions].
 */
internal fun List<LookupResult>.mergeSpellingVariants(
    dictionaryOrder: List<String>,
    support: LanguageSupport,
): List<LookupResult> {
    if (size < 2) return this
    val rank = dictionaryOrder.withIndex().associate { it.value to it.index }
    fun mergeKey(result: LookupResult): Triple<String, String, List<Pair<String, String>>>? {
        val reading = support.normalizeReading(result.term.reading.ifEmpty { result.term.expression })
        if (reading.isEmpty()) return null
        val primary = result.term.glossaries.minByOrNull { rank[it.dictionary] ?: Int.MAX_VALUE }?.dictionary ?: return null
        val primaryContents = result.term.glossaries
            .filter { it.dictionary == primary }
            .map { it.dictionary to it.content }
        return Triple(result.matched, reading, primaryContents)
    }
    val merged = LinkedHashMap<Any, LookupResult>()
    for ((index, result) in withIndex()) {
        val key: Any = mergeKey(result) ?: index
        val existing = merged[key]
        if (existing == null) {
            merged[key] = result
        } else {
            val alt = result.term.expression
            val alts = if (alt != existing.term.expression && alt !in existing.term.alternativeExpressions) {
                existing.term.alternativeExpressions + alt
            } else {
                existing.term.alternativeExpressions
            }
            val combined = existing.joined(result.term, dictionaryOrder, dedupeByContent = true)
            merged[key] = combined.copy(term = combined.term.copy(alternativeExpressions = alts))
        }
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

/**
 * [expression] read as [reading] spelled in the script [tag], for Anki's script markers: [expression] itself when it is
 * written in [tag]; otherwise the first of its likely conversions ([LanguageSupport.inScript]), and of [expression]
 * itself where it is written alike in both scripts, that [listed] finds as a word with the same reading. Characters
 * with several forms are different words (干 gān: 乾 "dry", 幹 gàn "to do"), so the reading rules out some. Without
 * such a word, the most likely conversion, or [expression] where it is written alike in both.
 */
internal suspend fun spelling(
    expression: String,
    reading: String,
    tag: String,
    support: LanguageSupport,
    listed: suspend (String) -> List<LookupResult>,
): String {
    val script = support.scriptOf(expression)
    if (script == tag) return expression
    val conversions = support.inScript(MappedText.identity(expression), tag).map { it.text }
    if (conversions.isEmpty()) return expression
    val candidates = if (script == null) conversions + expression else conversions
    val key = support.normalizeReading(support.displayReading(reading))
    return candidates.firstOrNull { candidate ->
        listed(candidate).any {
            it.term.expression == candidate && support.normalizeReading(support.displayReading(it.term.reading)) == key
        }
    } ?: if (script == null) expression else conversions.first()
}

/** This result with [other]'s definitions, frequencies and pronunciations that it lacks. */
private fun LookupResult.joined(
    other: TermEntry,
    dictionaryOrder: List<String>,
    dedupeByContent: Boolean = false,
): LookupResult {
    val existingKeys = if (dedupeByContent) term.glossaries.mapTo(HashSet()) { it.dictionary to it.content } else null
    val glossaries = if (existingKeys != null) {
        other.glossaries.filter { (it.dictionary to it.content) !in existingKeys }
    } else {
        other.glossaries.filter { it !in term.glossaries }
    }
    val frequencies = other.frequencies.filter { new -> term.frequencies.none { it.dictionary == new.dictionary } }
    val pitches = other.pitches.filter { new -> term.pitches.none { it.dictionary == new.dictionary } }
    if (glossaries.isEmpty() && frequencies.isEmpty() && pitches.isEmpty()) return this
    val rank = dictionaryOrder.withIndex().associate { it.value to it.index }
    val targetReading = if (term.reading.isEmpty() || term.reading == term.expression) {
        other.reading.ifEmpty { term.reading }
    } else {
        term.reading
    }
    return copy(
        term = term.copy(
            reading = targetReading,
            glossaries = (term.glossaries + glossaries).sortedBy { rank[it.dictionary] ?: Int.MAX_VALUE },
            frequencies = term.frequencies + frequencies,
            pitches = term.pitches + pitches,
        ),
    )
}
