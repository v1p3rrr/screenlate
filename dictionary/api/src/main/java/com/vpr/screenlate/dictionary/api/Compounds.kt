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
 * Splits a compound written as one word (Haustürschlüssel) into dictionary words: the longest word the dictionaries
 * have at the start, then the longest in the rest, a linking element between two parts skipped (Arbeit-s-zimmer).
 * Shorter words and skipped links are tried only when the longer choice leaves a rest that cannot be covered.
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
        val uncovered = HashSet<Int>()
        var lookups = 0

        suspend fun lengthsAt(start: Int): List<Int>? = known[start] ?: run {
            if (lookups++ >= MAX_LOOKUPS) return null
            prefixes(word.substring(start))
                .filter { it >= MIN_PART_LENGTH && start + it <= word.length }
                .distinct()
                .sortedDescending()
                .also { known[start] = it }
        }

        // Parts from [position] to the end; after the first part a link may come before the next one.
        suspend fun cover(position: Int, first: Boolean): List<IntRange>? {
            if (position == word.length) return emptyList()
            if (position in uncovered) return null
            val starts = if (first) {
                listOf(position)
            } else {
                listOf(position) + links.filter { word.startsWith(it, position, ignoreCase = true) }.map { position + it.length }
            }
            for (start in starts.filter { it < word.length }) {
                for (length in lengthsAt(start) ?: return null) {
                    val rest = cover(start + length, first = false) ?: continue
                    return listOf(start until start + length) + rest
                }
            }
            uncovered += position
            return null
        }

        return cover(0, first = true)?.takeIf { it.size >= 2 }
    }
}
