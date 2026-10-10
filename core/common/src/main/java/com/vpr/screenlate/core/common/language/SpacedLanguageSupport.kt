package com.vpr.screenlate.core.common.language

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
     * A period ends the sentence unless a letter or digit follows it at once (3.14, p.m, example.com) or it closes an
     * abbreviation. An abbreviation at the real end of a sentence keeps it running on, which is the smaller error.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        if (character != '.') return true
        if (index + 1 < text.length && text[index + 1].isLetterOrDigit()) return false
        return wordBefore(text, index) !in abbreviations
    }

    /** A word starts with a letter or digit; phrases and inflections are tried by the engine. */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        return if (Character.isLetterOrDigit(text.codePointAt(0))) LookupStart.Any else null
    }

    /** The whole word under the aim is looked up from its start, as Yomitan's word scan resolution does. */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !isWordCharacter(aimed.codePointAt(0))) return 0
        var index = before.length
        var count = 0
        while (index > 0) {
            val codePoint = before.codePointBefore(index)
            val size = Character.charCount(codePoint)
            val inWord = isWordCharacter(codePoint) ||
                (codePoint.toChar() in WORD_CONNECTORS && index > size && isWordCharacter(before.codePointBefore(index - size)))
            if (!inWord) break
            index -= size
            count++
        }
        return count
    }

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null

    /** The word before the period at [index], lowercase, with its inner periods: "p.m" in "at 5 p.m.". */
    private fun wordBefore(text: String, index: Int): String {
        var start = index
        while (start > 0) {
            val previous = text[start - 1]
            val inWord = previous.isLetter() || previous == '.' && start > 1 && text[start - 2].isLetter()
            if (!inWord) break
            start--
        }
        return text.substring(start, index).lowercase()
    }

    private fun isWordCharacter(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

    private companion object {
        /** Characters inside one word: don't, rock'n'roll, well-known. */
        const val WORD_CONNECTORS = "'’-"
    }
}
