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

/**
 * [results] for a tap on a later part of a hyphenated word: the words found from the whole word's start that reach the
 * aimed character at [aimedCharAt] (state-owned) come first, then those from the [parts] starts
 * (`LanguageSupport.partStarts`) that reach it (owned), each start in turn. Words that stop before the aimed character
 * (state) are left out, unless nothing reaches it. A start at a hyphen keeps only the suffix entries that take the
 * whole part (1990-ға: -ға), not a shorter one at its start (19-декабрь: not -де).
 *
 * @param text the lookup text, which [parts] index.
 * @param lookup the results from a UTF-16 index of the lookup text.
 */
internal suspend fun withHyphenatedParts(
    text: String,
    results: List<LookupResult>,
    aimedCharAt: Int,
    parts: List<Int>,
    lookup: suspend (Int) -> List<LookupResult>,
): List<LookupResult> {
    val found = parts.flatMap { start ->
        val end = if (Character.isLetterOrDigit(text.codePointAt(start))) start else partEnd(text, start)
        lookup(start).map { it.copy(start = start) }.filter { it.covers(aimedCharAt) && start + it.matched.length >= end }
    }
    val whole = results.filter { it.covers(aimedCharAt) }
    if (found.isEmpty()) return whole.ifEmpty { results }
    return (whole + found).distinctBy { it.term.expression to it.term.reading }
}

/** The end of the part after the hyphen at [hyphen]: its letters, digits and marks. */
private fun partEnd(text: String, hyphen: Int): Int {
    var end = hyphen + Character.charCount(text.codePointAt(hyphen))
    while (end < text.length) {
        val codePoint = text.codePointAt(end)
        val type = Character.getType(codePoint)
        val inPart = Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
        if (!inPart) break
        end += Character.charCount(codePoint)
    }
    return end
}

/** Whether the match covers the UTF-16 index [at] of the lookup text. */
fun LookupResult.covers(at: Int): Boolean = start <= at && start + matched.length > at
