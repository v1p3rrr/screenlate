package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.dictionary.api.model.LookupResult

/**
 * Results of a lookup that starts at the words before the aimed one as well (`LanguageSupport.earlierStartOffset`):
 * words found from an earlier start that reach the aimed word at [aimedAt] come first, the earliest start first, then
 * the aimed word's own results. Each result carries its start ([LookupResult.start]); a word found from two starts
 * is kept at the first.
 *
 * @param starts the UTF-16 indexes of [text] before [aimedAt] where words start (`LanguageSupport.wordStarts`).
 * @param lookup the results from a UTF-16 index of [text].
 */
internal suspend fun fromEarlierStarts(
    text: String,
    aimedAt: Int,
    starts: List<Int>,
    lookup: suspend (Int) -> List<LookupResult>,
): List<LookupResult> {
    val earlier = starts.flatMap { start ->
        lookup(start).filter { it.matched.length > aimedAt - start }.map { it.copy(start = start) }
    }
    val aimed = lookup(aimedAt).map { it.copy(start = aimedAt) }
    return (earlier + aimed).distinctBy { it.term.expression to it.term.reading }
}

/**
 * [results] as they are when one reaches the aimed character at [aimedCharAt]; otherwise the words from the first of
 * the [inner] starts (`LanguageSupport.innerStarts`) that reach it come first: aimed at ពេញ in ក្រុងភ្នំពេញ, which a
 * segmenter takes as one word and a dictionary has only as ក្រុង and ភ្នំពេញ, ភ្នំពេញ comes before ក្រុង.
 *
 * @param lookup the results from a UTF-16 index of the lookup text.
 */
internal suspend fun reachingAimedCharacter(
    results: List<LookupResult>,
    aimedCharAt: Int,
    inner: List<Int>,
    lookup: suspend (Int) -> List<LookupResult>,
): List<LookupResult> {
    if (results.any { it.covers(aimedCharAt) }) return results
    for (start in inner) {
        val found = lookup(start).map { it.copy(start = start) }.filter { it.covers(aimedCharAt) }
        if (found.isNotEmpty()) return (found + results).distinctBy { it.term.expression to it.term.reading }
    }
    return results
}

/** Whether the match covers the UTF-16 index [at] of the lookup text. */
fun LookupResult.covers(at: Int): Boolean = start <= at && start + matched.length > at
