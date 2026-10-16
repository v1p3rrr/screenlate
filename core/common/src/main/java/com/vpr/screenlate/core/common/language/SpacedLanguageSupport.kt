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
     * Words written joined to the next one with an apostrophe without being part of it (French l', qu'), lowercase: a
     * word aimed after the apostrophe is looked up from there ("l'eau" → "eau").
     */
    open val elisions: Set<String> = emptySet()

    /**
     * Whether a period after a number of one or two digits makes it an ordinal (German "am 3. Oktober"), so it does
     * not end the sentence; a year at the end of a sentence still does.
     */
    open val ordinalPeriod: Boolean = false

    /**
     * A period ends the sentence unless a letter or digit follows it at once (3.14, p.m, example.com), it closes an
     * abbreviation or, with [ordinalPeriod], an ordinal number. An abbreviation at the real end of a sentence keeps it
     * running on, which is the smaller error.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        if (character != '.') return true
        if (index + 1 < text.length && text[index + 1].isLetterOrDigit()) return false
        if (ordinalPeriod && digitsBefore(text, index) in 1..2) return false
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
            if (codePoint.toChar() in APOSTROPHES && isElision(before, index - size)) break
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

    /** The number of digits right before [index]; 0 when they follow a letter (A4). */
    private fun digitsBefore(text: String, index: Int): Int {
        var start = index
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start > 0 && text[start - 1].isLetter()) return 0
        return index - start
    }

    /** Whether the word ending at [end], just before an apostrophe, is one of [elisions]. */
    private fun isElision(text: String, end: Int): Boolean {
        if (elisions.isEmpty()) return false
        var start = end
        while (start > 0 && text[start - 1].isLetter()) start--
        return text.substring(start, end).lowercase() in elisions
    }

    private fun isWordCharacter(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

    private companion object {
        /** Characters inside one word: don't, rock'n'roll, well-known. */
        const val WORD_CONNECTORS = "'’-"
        const val APOSTROPHES = "'’"
    }
}
