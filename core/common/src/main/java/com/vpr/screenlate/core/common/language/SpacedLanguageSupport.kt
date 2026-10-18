package com.vpr.screenlate.core.common.language

import java.util.Locale
import kotlin.math.max

/**
 * Rules shared by languages written with spaces between words. Case, diacritics and inflections are left to Yomitan's
 * text processors and transforms in the engine; the text side only finds where a word starts. Subclasses give the
 * per-language data.
 */
abstract class SpacedLanguageSupport : LanguageSupport {
    final override val wordSeparator = " "
    final override val searchResolution = SearchResolution.WORD
    override val sentenceTerminators: Set<Char> = setOf('.', '!', '?', '…', '\n')

    /**
     * Pairs whose closing character differs from the opening one: a straight quote or an apostrophe (’) does not tell
     * whether it opens or closes.
     */
    override val quotePairs: Map<Char, Char> = mapOf('“' to '”', '(' to ')', '[' to ']')

    override val ankiMarkers: List<String> = emptyList()

    /** Recordings of words first, then the phone's text to speech. */
    override val defaultAudioSources: List<String> = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * Words that end with a period without ending the sentence ("Mr.", "e.g."), lowercase and without the final
     * period.
     */
    open val abbreviations: Set<String> = emptySet()

    /**
     * Single letters, lowercase, that abbreviate only together with another one written apart (German "z. B.",
     * "d. h."): a period after one keeps the sentence going only when a letter with its period stands right before or
     * after it, so "Plan B." still ends a sentence.
     */
    open val pairedLetters: Set<String> = emptySet()

    /**
     * Abbreviations that are words as well (Polish "ok." for około, and "ok"): they keep the sentence going only before
     * a number or a lowercase word, so "Jest ok. Idziemy." still ends one.
     */
    open val abbreviationsBeforeLowercase: Set<String> = emptySet()

    /**
     * Words written joined to the next one with an apostrophe without being part of it (French l', qu'), lowercase: a
     * word aimed after the apostrophe is looked up from there ("l'eau" → "eau").
     */
    open val elisions: Set<String> = emptySet()

    /**
     * Case rules of the language's letters, for matching [abbreviations] and [elisions] written in capitals. The root
     * rules are tried as well, for capitals read without their dots (ŞTI for ŞTİ).
     */
    protected open val caseLocale: Locale = Locale.ROOT

    /**
     * Whether a period after a number makes it an ordinal, so it does not end the sentence: a number of one or two
     * digits (German "am 3. Oktober"), or a longer one followed by a lowercase word (Latvian "1918. gada"). A year at
     * the end of a sentence still ends it.
     */
    open val ordinalPeriod: Boolean = false

    /**
     * A period ends the sentence unless a letter or digit follows it at once (3.14, p.m, example.com), it closes an
     * abbreviation (a paired letter only beside another, see [pairedLetters]) or, with [ordinalPeriod], an ordinal
     * number. An abbreviation at the real end of a sentence keeps it running on, which is the smaller error.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        if (character != '.') return true
        if (index + 1 < text.length && text[index + 1].isLetterOrDigit()) return false
        if (ordinalPeriod && isOrdinal(text, index)) return false
        val word = wordBefore(text, index)
        if (word.length == 1 && word.lowercaseIn(pairedLetters)) return !hasPartner(text, index - 1, index)
        if (word.lowercaseIn(abbreviationsBeforeLowercase)) return !lowercaseOrNumberAfter(text, index)
        return !word.lowercaseIn(abbreviations)
    }

    /** A word starts with a letter or digit; phrases and inflections are tried by the engine. */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        return if (Character.isLetterOrDigit(text.codePointAt(0))) LookupStart.Any else null
    }

    /** The whole word under the aim is looked up from its start, as Yomitan's word scan resolution does. */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !isWordCharacter(aimed.codePointAt(0))) return 0
        return wordLengthBefore(before)
    }

    /** The characters (code points) of the word that [before] ends with, its connectors included. */
    private fun wordLengthBefore(before: String): Int {
        var index = before.length
        var count = 0
        while (index > 0) {
            val codePoint = before.codePointBefore(index)
            val size = Character.charCount(codePoint)
            if (codePoint.toChar() in APOSTROPHES && isElision(before, index - size)) break
            val inWord = isWordCharacter(codePoint) ||
                (codePoint.toChar() in WORD_CONNECTORS && index > size && isWordCharacter(before.codePointBefore(index - size)))
            if (!inWord) break
            index -= size
            count++
        }
        return count
    }

    /** Words before the aimed one that the lookup starts at as well; see [earlierStartOffset]. */
    open val earlierWords: Int = 0

    /**
     * The start of the [earlierWords]-th word before, or of the furthest one that only spaces separate; or the start
     * of a word's first half hyphenated at a line end right before the aim ("impor- " before "tant", see
     * [spellingVariants]), whichever is further.
     */
    override fun earlierStartOffset(before: String): Int = max(earlierWordsOffset(before), hyphenatedStartOffset(before))

    private fun earlierWordsOffset(before: String): Int {
        var index = before.length
        var count = 0
        var offset = 0
        repeat(earlierWords) {
            val end = count
            while (index > 0 && Character.isSpaceChar(before[index - 1])) {
                index--
                count++
            }
            val spaces = count - end
            while (index > 0 && isWordCharacter(before.codePointBefore(index))) {
                index -= Character.charCount(before.codePointBefore(index))
                count++
            }
            if (spaces == 0 || count == end + spaces) return offset
            offset = count
        }
        return offset
    }

    /**
     * A word hyphenated at a line end, the line's separator after the hyphen ("impor- tant"): tried joined
     * ("important") when the second half starts in lowercase, and with the hyphen kept ("well- known" as
     * "well-known"). Halves that are words of their own ("Haus- und Gartenarbeit") find nothing joined and keep the
     * results of the text as written.
     */
    override fun spellingVariants(text: MappedText): List<MappedText> {
        if (!HYPHEN_BREAK.containsMatchIn(text.text)) return emptyList()
        return listOf(text.replace(HYPHEN_JOINED, ""), text.replace(HYPHEN_KEPT, "$1"))
            .filter { it.text != text.text }
            .distinctBy { it.text }
    }

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null

    /** The word before the period at [index] with its inner periods: "p.m" in "at 5 p.m.". */
    private fun wordBefore(text: String, index: Int): String {
        var start = index
        while (start > 0) {
            val previous = text[start - 1]
            val inWord = previous.isLetter() || previous == '.' && start > 1 && text[start - 2].isLetter()
            if (!inWord) break
            start--
        }
        return text.substring(start, index)
    }

    /**
     * Whether the letter at [letter], with its period at [period], has another letter with a period right before or
     * after it, spaces between them: "z. B.".
     */
    private fun hasPartner(text: String, letter: Int, period: Int): Boolean {
        var before = letter
        while (before > 0 && text[before - 1].isWhitespace()) before--
        val partnerBefore = before < letter && before >= 2 && text[before - 1] == '.' && text[before - 2].isLetter() &&
            (before < 3 || !text[before - 3].isLetter())
        var after = period + 1
        while (after < text.length && text[after].isWhitespace()) after++
        val partnerAfter = after > period + 1 && after + 1 < text.length && text[after].isLetter() &&
            text[after + 1] == '.'
        return partnerBefore || partnerAfter
    }

    /** Whether the first character after the spaces that follow [index] is a lowercase letter or a digit. */
    private fun lowercaseOrNumberAfter(text: String, index: Int): Boolean {
        var next = index + 1
        while (next < text.length && text[next] != '\n' && text[next].isWhitespace()) next++
        return next < text.length && (text[next].isLowerCase() || text[next].isDigit())
    }

    /** The start of the first half of a word hyphenated at a line end that [before] ends with; 0 for none. */
    private fun hyphenatedStartOffset(before: String): Int {
        var index = before.length
        while (index > 0 && before[index - 1].isWhitespace()) index--
        if (index == before.length || index == 0 || before[index - 1] !in HYPHENS) return 0
        val half = wordLengthBefore(before.substring(0, index - 1))
        return if (half == 0) 0 else before.length - index + 1 + half
    }

    /** Whether the period at [index] closes an ordinal number (see [ordinalPeriod]). */
    private fun isOrdinal(text: String, index: Int): Boolean {
        val digits = digitsBefore(text, index)
        if (digits == 0) return false
        if (digits <= 2) return true
        var next = index + 1
        while (next < text.length && text[next] != '\n' && text[next].isWhitespace()) next++
        return next < text.length && text[next].isLowerCase()
    }

    /** The number of digits right before [index]; 0 when they follow a letter (A4). */
    private fun digitsBefore(text: String, index: Int): Int {
        var start = index
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start > 0 && text[start - 1].isLetter()) return 0
        return index - start
    }

    /**
     * Whether the word ending at [end], just before an apostrophe, is one of [elisions]. A word after another
     * apostrophe is part of a longer one (the n of rock'n'roll), not an elided word.
     */
    private fun isElision(text: String, end: Int): Boolean {
        if (elisions.isEmpty()) return false
        var start = end
        while (start > 0 && text[start - 1].isLetter()) start--
        if (start > 0 && text[start - 1] in APOSTROPHES) return false
        return text.substring(start, end).lowercaseIn(elisions)
    }

    private fun String.lowercaseIn(words: Set<String>): Boolean =
        lowercase(caseLocale) in words || lowercase(Locale.ROOT) in words

    private fun isWordCharacter(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

    private companion object {
        /** Characters inside one word: don't, rock'n'roll, well-known. */
        const val WORD_CONNECTORS = "'’-"
        const val APOSTROPHES = "'’"

        /** A hyphen, the Unicode hyphen and the soft hyphen, which marks a break inside a word. */
        const val HYPHENS = "-‐\u00AD"
        val HYPHEN_BREAK = Regex("(?<=\\p{L})[\\-‐\u00AD]\\s+(?=\\p{L})")
        val HYPHEN_JOINED = Regex("(?<=\\p{L})[\\-‐\u00AD]\\s+(?=\\p{Ll})")
        val HYPHEN_KEPT = Regex("(?<=\\p{L})([\\-‐])\\s+(?=\\p{L})")
    }
}
