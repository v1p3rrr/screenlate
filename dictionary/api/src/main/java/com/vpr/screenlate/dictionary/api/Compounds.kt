package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.dictionary.api.model.LookupResult

/**
 * The entries of each part of a compound, in the order of the parts, when the leading word of [text] has no entry of
 * its own among [results] (see [Compounds]). Each entry's match is the whole word, which the page marks as read. Null
 * when the word has an entry or no split covers it.
 *
 * @param lookup the engine's results for a text, with every word the text starts with.
 */
internal suspend fun compoundParts(
    text: String,
    results: List<LookupResult>,
    links: List<String>,
    lookup: suspend (String) -> List<LookupResult>,
): List<List<LookupResult>>? {
    val word = Compounds.word(text)
    if (results.any { it.matched.length >= word.length }) return null
    val found = HashMap<Int, List<LookupResult>>()
    val parts = Compounds.split(word, links) { rest ->
        lookup(rest).also { found[word.length - rest.length] = it }.map { it.matched.length }
    } ?: return null
    return parts.map { part ->
        found.getValue(part.first).filter { it.matched.length == part.count() }.map { it.copy(matched = word) }
    }
}

/**
 * The entries of a compound's parts in order, each marked with the parts ([LookupResult.compound]): the headword of
 * each part's first entry. A part left without entries is left out.
 */
internal fun List<List<LookupResult>>.markedAsCompound(): List<LookupResult> {
    val parts = filter { it.isNotEmpty() }
    val names = parts.map { it.first().term.expression }
    return parts.flatMap { part -> part.map { it.copy(compound = names) } }
}

/** The results with a capitalized headword (German nouns) first, the others after them, each in their order. */
internal fun List<LookupResult>.capitalizedFirst(): List<LookupResult> =
    sortedBy { it.term.expression.firstOrNull()?.isUpperCase() != true }

/**
 * Splits a compound written as one word (Haustürschlüssel) into dictionary words, a linking element between two parts
 * skipped (Arbeit-s-zimmer). Of the splits that cover the word, the one with the fewest parts wins, then the one whose
 * shortest part is longest (Dutch stads + bestuurder, not stadsbestuur + der), then the one with the longer words
 * first. Splits are tried longest word first, so when the lookups run out the longest-first one found so far stays.
 */
internal object Compounds {

    /** Parts shorter than this are not tried: two letters match too many particles and abbreviations. */
    const val MIN_PART_LENGTH = 3

    /** Prefix lookups one split may take; each costs an engine lookup. */
    const val MAX_LOOKUPS = 12

    /** The leading word of [text]: its letters up to the first other character. */
    fun word(text: String): String {
        var end = 0
        while (end < text.length && text[end].isLetter()) end++
        return text.substring(0, end)
    }

    /**
     * The parts covering [word] in order, as ranges of it, without the skipped [links]; null when no split into two
     * or more parts covers it within [MAX_LOOKUPS].
     *
     * @param prefixes the lengths of the dictionary words its argument starts with.
     */
    suspend fun split(word: String, links: List<String>, prefixes: suspend (String) -> Collection<Int>): List<IntRange>? {
        if (word.length < 2 * MIN_PART_LENGTH) return null
        val known = HashMap<Int, List<Int>>()
        val best = HashMap<Int, List<IntRange>?>()
        var lookups = 0

        // A start whose lookup the limit leaves out has no words.
        suspend fun lengthsAt(start: Int): List<Int> = known[start] ?: run {
            if (lookups >= MAX_LOOKUPS) return emptyList()
            lookups++
            prefixes(word.substring(start))
                .filter { it >= MIN_PART_LENGTH && start + it <= word.length }
                .distinct()
                .sortedDescending()
                .also { known[start] = it }
        }

        // The best parts from [position] to the end; after the first part a link may come before the next one.
        suspend fun cover(position: Int, first: Boolean): List<IntRange>? {
            if (position == word.length) return emptyList()
            if (!first && position in best) return best[position]
            val starts = if (first) {
                listOf(position)
            } else {
                listOf(position) + links.filter { word.startsWith(it, position, ignoreCase = true) }.map { position + it.length }
            }
            var chosen: List<IntRange>? = null
            for (start in starts.filter { it < word.length }) {
                for (length in lengthsAt(start)) {
                    val rest = cover(start + length, first = false) ?: continue
                    val parts = listOf(start until start + length) + rest
                    if (chosen == null || parts.isBetterThan(chosen)) chosen = parts
                }
            }
            if (!first) best[position] = chosen
            return chosen
        }

        return cover(0, first = true)?.takeIf { it.size >= 2 }
    }

    private fun List<IntRange>.isBetterThan(other: List<IntRange>): Boolean =
        size < other.size || size == other.size && shortest() > other.shortest()

    private fun List<IntRange>.shortest(): Int = minOf { it.last - it.first + 1 }
}
