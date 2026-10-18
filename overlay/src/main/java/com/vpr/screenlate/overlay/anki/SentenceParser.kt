package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.SearchResolution
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
internal data class SentencePart(val text: String, val expression: String? = null, val reading: String? = null)

/**
 * Longest dictionary matches for a note's sentence, all in its captured language. Text no dictionary knows is passed
 * over a character at a time, or a word at a time in a language looked up by words, so a match never starts inside a
 * word (happy in unhappy).
 */
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
            val next = unmatchedEnd(sentence, offset, language.support)
            val text = sentence.substring(offset, next)
            val last = parts.lastOrNull()
            if (last != null && last.expression == null) parts[parts.lastIndex] = last.copy(text = last.text + text)
            else parts += SentencePart(text)
            offset = next
        }
    }
    return parts
}

/**
 * The end of the unmatched text at [offset]: the next code point, or the rest of the word by the language's rules
 * ([LanguageSupport.wordStartOffset]), with the apostrophes and hyphens inside it (aujourd'hui, well-known).
 */
private fun unmatchedEnd(sentence: String, offset: Int, support: LanguageSupport): Int {
    var end = sentence.offsetByCodePoints(offset, 1)
    if (support.searchResolution != SearchResolution.WORD) return end
    // Whether the code point at [at] belongs to the word that starts at [offset].
    fun continues(at: Int): Boolean {
        if (at >= sentence.length) return false
        val aimed = sentence.substring(at, sentence.offsetByCodePoints(at, 1))
        return support.wordStartOffset(sentence.substring(offset, at), aimed) == sentence.codePointCount(offset, at)
    }
    while (end < sentence.length) {
        val next = sentence.offsetByCodePoints(end, 1)
        end = when {
            continues(end) -> next
            continues(next) -> sentence.offsetByCodePoints(next, 1)
            else -> break
        }
    }
    return end
}
