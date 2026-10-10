package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
internal data class SentencePart(val text: String, val expression: String? = null, val reading: String? = null)

/** Longest dictionary matches for a note's sentence, all in its captured language. */
internal suspend fun parseSentence(
    sentence: String,
    language: Language,
    lookup: suspend (String, Language) -> LookupResult?,
): List<SentencePart> {
    val parts = mutableListOf<SentencePart>()
    var offset = 0
    while (offset < sentence.length) {
        val result = try {
            lookup(sentence.substring(offset), language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val matched = result?.matched?.takeIf { it.isNotEmpty() && sentence.startsWith(it, offset) }
        if (result != null && matched != null) {
            parts += SentencePart(matched, result.term.expression, result.term.reading)
            offset += matched.length
        } else {
            val next = sentence.offsetByCodePoints(offset, 1)
            val text = sentence.substring(offset, next)
            val last = parts.lastOrNull()
            if (last != null && last.expression == null) parts[parts.lastIndex] = last.copy(text = last.text + text)
            else parts += SentencePart(text)
            offset = next
        }
    }
    return parts
}
