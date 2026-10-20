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
