package com.vpr.screenlate.core.common.language

import java.util.Locale

/**
 * Rules shared by languages written without spaces between words, whose words a dictionary-based segmenter finds
 * (ICU's word break iterator): a space ends a phrase or a sentence, not a word. The lookup starts at the start of the
 * segmenter's word under the aim and at the starts of the [earlierWords] words before it, as Vietnamese does with its
 * syllables, so a word the segmenter cuts into parts (ห้อง|สมุด) is found from any of them. Where nothing found from
 * the word's start reaches the aimed character, as in a word the segmenter joined from two that a dictionary has apart
 * (ក្រុងភ្នំពេញ), the lookup starts inside the word as well ([innerStarts]). A lookup never starts or
 * ends inside a cluster of a letter and its marks, and a match shorter than the longest from its start is shown only
 * where the segmenter ends a word (การ in การตลาด, not กา).
 */
abstract class SegmentedLanguageSupport(private val segmenter: WordSegmenter) : LanguageSupport {
    final override val wordSeparator = ""
    final override val searchResolution = SearchResolution.LETTER
    override val ankiMarkers: List<String> = emptyList()

    /** Recordings of words first, then the phone's text to speech. */
    override val defaultAudioSources: List<String> = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Words before the aimed one that the lookup starts at as well; two reach every part of most words. */
    open val earlierWords: Int = 2

    /** Whether [codePoint] is a character of the language's script, its marks and digits included. */
    protected abstract fun inScript(codePoint: Int): Boolean

    /**
     * Whether [codePoint] goes with the character before it: a combining mark, or a vowel written after its consonant
     * that is a letter of its own (Thai า, ำ).
     */
    protected open fun continuesCluster(codePoint: Int): Boolean = isMark(codePoint)

    /**
     * Whether [codePoint] goes with the character after it: a vowel written before the consonant it is read after
     * (Thai เ), or the sign that puts the next consonant below (Khmer coeng).
     */
    protected open fun holdsNext(codePoint: Int): Boolean = false

    /**
     * Whether a word may start at [index] of [text], a letter or digit that starts a cluster: not at the repetition and
     * abbreviation marks, which go with the word before them.
     */
    protected open fun mayStartWord(text: String, index: Int): Boolean = true

    private val locale: Locale by lazy { Locale.forLanguageTag(languageTag) }

    /** The pieces' boundaries of [text], as [WordSegmenter.boundaries]. */
    protected fun boundaries(text: String): IntArray = segmenter.boundaries(text, locale)

    /** A word starts with a letter or digit outside a cluster. */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? =
        if (startsWord(text, 0)) LookupStart.Any else null

    override fun wordStartOffset(before: String, aimed: String): Int = wordStartOffset(before, aimed, "")

    /**
     * The start of the segmenter's word that the aimed character is in, read with the text around it; a repetition mark
     * the segmenter parts from its word (Khmer កូន|ៗ) belongs to that word.
     */
    override fun wordStartOffset(before: String, aimed: String, after: String): Int {
        if (aimed.isEmpty()) return 0
        val codePoint = aimed.codePointAt(0)
        if (!Character.isLetterOrDigit(codePoint) && !continuesCluster(codePoint)) return 0
        val text = before + aimed + after
        val at = before.length
        val start = boundaries(text).lastOrNull { it <= at && isClusterStart(text, it) && mayStartWord(text, it) }
            ?: return 0
        return if (startsWord(text, start)) text.codePointCount(start, at) else 0
    }

    override fun earlierStartOffset(before: String): Int = earlierStartOffset(before, "")

    /**
     * The start of the [earlierWords]-th word of the script before the aimed one, or of the furthest one that nothing
     * but words of the script separates from it; zero-width spaces and repetition marks between them are passed over.
     */
    override fun earlierStartOffset(before: String, from: String): Int {
        val text = before + from
        val at = before.length
        var offset = 0
        var count = 0
        for (start in earlierStarts(text, at).asReversed()) {
            if (count == earlierWords) break
            offset = text.codePointCount(start, at)
            count++
        }
        return offset
    }

    /** The starts of the words of the script that run up to [end] without a space or other piece between them. */
    private fun earlierStarts(text: String, end: Int): List<Int> {
        val pieces = boundaries(text).filter { it < end && isClusterStart(text, it) }
        val starts = ArrayList<Int>()
        for (start in pieces.asReversed()) {
            val codePoint = text.codePointAt(start)
            if (codePoint == ZWSP || (inScript(codePoint) && !mayStartWord(text, start))) continue
            if (!isScriptWord(text, start)) break
            starts += start
        }
        return starts.asReversed()
    }

    /** Where the segmenter starts words of the script before [end]. */
    override fun wordStarts(text: String, end: Int): List<Int> =
        boundaries(text).filter { it < end && isClusterStart(text, it) && isScriptWord(text, it) }

    /** The clusters of the script after [from] up to the one [to] is in, where a word may start. */
    override fun innerStarts(text: String, from: Int, to: Int): List<Int> =
        (from + 1..minOf(to, text.length - 1)).filter { isScriptWord(text, it) }

    /**
     * Matches that end inside a cluster are left out; of the others, the longest is shown, and shorter ones where the
     * segmenter ends a word of [text].
     */
    override fun shownMatchLengths(text: String, lengths: Set<Int>): Set<Int> {
        val whole = lengths.filter { it in 1..text.length && (it == text.length || isClusterStart(text, it)) }
        val longest = whole.maxOrNull() ?: return emptySet()
        val ends = boundaries(text).toHashSet()
        return whole.filterTo(HashSet()) { it == longest || it in ends }
    }

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null

    /** Whether a cluster starts at [index] of [text]: no mark or following vowel there, no leading vowel before it. */
    protected fun isClusterStart(text: String, index: Int): Boolean {
        if (index >= text.length) return false
        if (continuesCluster(text.codePointAt(index))) return false
        return index == 0 || !holdsNext(text.codePointBefore(index))
    }

    /** Whether a word may start at [index] of [text]: a letter or digit that starts a cluster. */
    private fun startsWord(text: String, index: Int): Boolean {
        if (!isClusterStart(text, index)) return false
        return Character.isLetterOrDigit(text.codePointAt(index)) && mayStartWord(text, index)
    }

    /** Whether a word of the script's letters starts at [index] of [text]. */
    private fun isScriptWord(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)
        return startsWord(text, index) && Character.isLetter(codePoint) && inScript(codePoint)
    }

    protected fun isMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    protected companion object {
        /** The zero-width space, which Khmer text puts between words. */
        const val ZWSP = 0x200B
    }
}
