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

    /** Apostrophes inside [abbreviations] (Uzbek "ko'ch." beside "koʻch."): the word before a period runs over them. */
    protected open val abbreviationApostrophes: String = ""

    /**
     * Single letters, lowercase, that abbreviate only together with another one written apart (German "z. B.",
     * "d. h."): a period after one keeps the sentence going only when a letter with its period stands right before or
     * after it, so "Plan B." still ends a sentence at the end of a text or before a lowercase word (before a capital
     * it is taken as an initial).
     */
    open val pairedLetters: Set<String> = emptySet()

    /**
     * Abbreviations that are words as well (Polish "ok." for około, and "ok"): they keep the sentence going only before
     * a number or a lowercase word, so "Jest ok. Idziemy." still ends one.
     */
    open val abbreviationsBeforeLowercase: Set<String> = emptySet()

    /**
     * Abbreviations that keep the sentence going only when written with a capital, lowercase: the Latin praenomina
     * (Sex. for Sextus, M. for Marcus), which in lowercase are words or units that end sentences (sex "six", m).
     */
    open val capitalizedAbbreviations: Set<String> = emptySet()

    /**
     * Letters as their names write them where they stand for initials or the letters of an acronym (Hindi ए, बी, सी for
     * A, B, C), lowercase: two or more joined by periods keep the sentence going ("ए.पी.जे.", "आई.आई.टी."). A single one
     * counts only when [abbreviations] lists it.
     */
    open val initials: Set<String> = emptySet()

    /**
     * The script whose words of one written syllable before a period are taken as initials or abbreviations (Marathi
     * "पु. ल. देशपांडे", "प.पू."): one akshara of it, a consonant or a conjunct with its signs, or a vowel, alone or joined
     * to others by periods, keeps the sentence going, unless it is one of [syllableSentenceEnds]. Other scripts' letters
     * are not counted.
     */
    open val syllableInitialScript: Character.UnicodeScript? = null

    /** Words of one syllable that end sentences (Marathi हो, मी, घ्या), lowercase; see [syllableInitialScript]. */
    open val syllableSentenceEnds: Set<String> = emptySet()

    /**
     * Words written joined to the next one with an apostrophe without being part of it (French l', qu'), lowercase: a
     * word aimed after the apostrophe is looked up from there ("l'eau" → "eau").
     */
    open val elisions: Set<String> = emptySet()

    /**
     * Words written joined to the next one with a hyphen without being part of it (the Romanian pronouns and
     * prepositions of s-a, l-am, într-o), lowercase: a word aimed after the hyphen is looked up from there.
     */
    open val hyphenElisions: Set<String> = emptySet()

    /**
     * Case rules of the language's letters, for matching [abbreviations] and [elisions] written in capitals. The root
     * rules are tried as well, for capitals read without their dots (ŞTI for ŞTİ).
     */
    protected open val caseLocale: Locale = Locale.ROOT

    /**
     * Whether a period after a number makes it an ordinal, so it does not end the sentence: a number of one or two
     * digits (German "am 3. Oktober"), or a longer one or a Roman numeral followed by a lowercase word (Latvian
     * "1918. gada", Czech "Karel IV. byl"). A year or a Roman numeral at the end of a sentence still ends it.
     */
    open val ordinalPeriod: Boolean = false

    /**
     * A period ends the sentence unless a letter or digit follows it at once (3.14, p.m, example.com), it closes an
     * initial (one capital letter before a capitalized word: J. Smith, К. Бакиев), an abbreviation (a paired letter
     * only beside another, see [pairedLetters]; one of [capitalizedAbbreviations] only when capitalized) or
     * [initials], a syllable of [syllableInitialScript], or, with [ordinalPeriod], an ordinal number. An abbreviation or a
     * capital letter at the real end of a sentence (vitamin C. It…) keeps it running on, which is the smaller error.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        if (character != '.') return true
        if (index + 1 < text.length && text[index + 1].isLetterOrDigit()) return false
        if (ordinalPeriod && isOrdinal(text, index)) return false
        val word = wordBefore(text, index)
        if (word.length == 1 && word[0].isUpperCase() && capitalWordAfter(text, index)) return false
        if (word.length == 1 && word.lowercaseIn(pairedLetters)) {
            return !hasPartner(text, index - 1, index)
        }
        if (word.lowercaseIn(abbreviationsBeforeLowercase)) return !lowercaseOrNumberAfter(text, index)
        if (word.firstOrNull()?.isUpperCase() == true && word.lowercaseIn(capitalizedAbbreviations)) return false
        if ('.' in word && word.split('.').all { it.lowercaseIn(initials) }) return false
        val syllableScript = syllableInitialScript
        if (syllableScript != null && word.split('.').all { isOneSyllable(it, syllableScript) } &&
            ('.' in word || word !in syllableSentenceEnds)
        ) {
            return false
        }
        return !word.lowercaseIn(abbreviations)
    }

    /**
     * A word starts with a letter or digit; phrases and inflections are tried by the engine. With [hyphenedEndings],
     * a hyphen right before a letter starts one as well, for the dictionaries' suffix entries (-ға, -vuotias).
     */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        if (hyphenedEndings != HyphenedEndings.NONE && text.length > 1 && text[0] == '-' && text[1].isLetter()) {
            return LookupStart.Any
        }
        return if (Character.isLetterOrDigit(text.codePointAt(0))) LookupStart.Any else null
    }

    /** The whole word under the aim is looked up from its start, as Yomitan's word scan resolution does. */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !isWordCharacter(aimed.codePointAt(0))) return 0
        return wordLengthBefore(before)
    }

    /** The characters (code points) of the word that [before] ends with, its connectors included. */
    protected fun wordLengthBefore(before: String): Int {
        var index = before.length
        var count = 0
        while (index > 0) {
            val codePoint = before.codePointBefore(index)
            val size = Character.charCount(codePoint)
            if (codePoint.toChar() in APOSTROPHES && isElision(before, index - size, elisions)) break
            if (codePoint.toChar() == '-' && isElision(before, index - size, hyphenElisions)) break
            val inWord = isWordCharacter(codePoint) ||
                (codePoint.toChar() in wordConnectors && index > size && isWordCharacter(before.codePointBefore(index - size)))
            if (!inWord) break
            index -= size
            count++
        }
        return count
    }

    /**
     * After each hyphen between word characters (and the Armenian hyphen, the Hebrew maqaf) up to the aimed character;
     * when the part before it [takesHyphenedEnding], the hyphen's own index comes first.
     */
    override fun partStarts(text: String, from: Int, to: Int): List<Int> {
        val starts = ArrayList<Int>()
        var part = from
        for (index in from + 2..minOf(to, text.length - 1)) {
            val hyphen = text[index - 1]
            if (hyphen !in PART_HYPHENS || hyphen !in wordConnectors) continue
            if (!isWordCharacter(text.codePointBefore(index - 1)) || !isWordCharacter(text.codePointAt(index))) continue
            if (takesHyphenedEnding(text.substring(part, index - 1))) starts += index - 1
            starts += index
            part = index
        }
        return starts
    }

    /**
     * Whether an ending written after [part] with a hyphen is looked up with the hyphen as well, for the dictionaries'
     * suffix entries; see [hyphenedEndings].
     */
    protected open fun takesHyphenedEnding(part: String): Boolean = when (hyphenedEndings) {
        HyphenedEndings.NONE -> false
        HyphenedEndings.AFTER_NUMBERS -> isNumber(part)
        HyphenedEndings.AFTER_NUMBERS_AND_CAPITALS -> isNumber(part) || isCapitalsAbbreviation(part)
        HyphenedEndings.AFTER_ANY -> true
    }

    /** Which parts of a hyphenated word an ending after the hyphen is looked up with the hyphen for. */
    protected open val hyphenedEndings: HyphenedEndings = HyphenedEndings.NONE

    protected enum class HyphenedEndings {
        NONE,

        /** Finnish: 1990-luvulla, 20-vuotias; the part after an abbreviation is a compound's (EU-maat). */
        AFTER_NUMBERS,

        /** Hungarian, Estonian, Azerbaijani, Uzbek: 2023-ban, EU-ban, NATO-ya. */
        AFTER_NUMBERS_AND_CAPITALS,

        /** Kazakh and Kyrgyz: endings after numbers and abbreviations (АҚШ-қа), particles after a word (сондай-ақ). */
        AFTER_ANY,
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
     * results of the text as written. A soft hyphen between letters is left out (Kan\u00ADnada as Kannada).
     */
    override fun spellingVariants(text: MappedText): List<MappedText> {
        val unbroken = text.replace(SOFT_HYPHEN_INSIDE, "")
        val joined = if (HYPHEN_BREAK.containsMatchIn(unbroken.text)) {
            listOf(unbroken.replace(HYPHEN_JOINED, ""), unbroken.replace(HYPHEN_KEPT, "$1"))
        } else {
            emptyList()
        }
        return (joined + unbroken).filter { it.text != text.text }.distinctBy { it.text }
    }

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null

    /**
     * The word before the period at [index] with its inner periods and [abbreviationApostrophes]: "p.m" in "at 5 p.m.".
     * The zero-width joiners inside it are passed over and left out (ಎನ್‌ as ಎನ್).
     */
    private fun wordBefore(text: String, index: Int): String {
        var start = index
        while (start > 0) {
            val previous = text[start - 1]
            val joining = previous == '.' || previous in abbreviationApostrophes
            val inWord = previous.isLetterOrMark() || previous.isJoiner() ||
                joining && start > 1 && text[start - 2].isLetterOrMark()
            if (!inWord) break
            start--
        }
        return text.substring(start, index).filterNot { it.isJoiner() }
    }

    /**
     * Whether [part] is one written syllable of [script]: one letter, or several joined by a virama or a zero-width
     * joiner (क्ष, श्री), with their vowel signs and marks (पु, प्रा).
     */
    private fun isOneSyllable(part: String, script: Character.UnicodeScript): Boolean {
        var letters = 0
        var joined = false
        for (character in part) {
            if (character.isLetter()) {
                if (Character.UnicodeScript.of(character.code) != script) return false
                if (!joined) letters++
            } else if (!character.isLetterOrMark() && character.code != ZWJ) {
                return false
            }
            joined = character in VIRAMAS || character.code == ZWJ
        }
        return letters == 1
    }

    /**
     * Whether the letter at [letter], with its period at [period], has another letter with a period right before or
     * after it, spaces between them: "z. B.".
     */
    private fun hasPartner(text: String, letter: Int, period: Int): Boolean {
        var before = letter
        while (before > 0 && text[before - 1].isWhitespace()) before--
        val partnerBefore = before < letter && before >= 2 && text[before - 1] == '.' && text[before - 2].isLetter() &&
            (before < 3 || !text[before - 3].isLetterOrMark())
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
        if (digits == 0) return ROMAN_NUMERAL.matches(wordBefore(text, index)) && lowercaseWordAfter(text, index)
        if (digits <= 2) return true
        return lowercaseWordAfter(text, index)
    }

    /** Whether a lowercase letter follows [index] after spaces on the same line. */
    private fun lowercaseWordAfter(text: String, index: Int): Boolean {
        var next = index + 1
        while (next < text.length && text[next] != '\n' && text[next].isWhitespace()) next++
        return next < text.length && text[next].isLowerCase()
    }

    /** Whether a capital letter follows [index] after spaces on the same line. */
    private fun capitalWordAfter(text: String, index: Int): Boolean {
        var next = index + 1
        while (next < text.length && text[next] != '\n' && text[next].isWhitespace()) next++
        return next < text.length && text[next].isUpperCase()
    }

    /** The number of digits right before [index]; 0 when they follow a letter (A4). */
    private fun digitsBefore(text: String, index: Int): Int {
        var start = index
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start > 0 && text[start - 1].isLetterOrMark()) return 0
        return index - start
    }

    /**
     * Whether the word ending at [end], just before an apostrophe or a hyphen, is one of [words] ([elisions],
     * [hyphenElisions]). A word after another apostrophe is part of a longer one (the n of rock'n'roll), not an elided
     * word.
     */
    private fun isElision(text: String, end: Int, words: Set<String>): Boolean {
        if (words.isEmpty()) return false
        var start = end
        while (start > 0 && text[start - 1].isLetterOrMark()) start--
        if (start > 0 && text[start - 1] in APOSTROPHES) return false
        return text.substring(start, end).lowercaseIn(words)
    }

    private fun String.lowercaseIn(words: Set<String>): Boolean =
        lowercase(caseLocale) in words || lowercase(Locale.ROOT) in words

    /**
     * Characters that join two words into one when they stand between word characters: the apostrophe and the hyphen
     * (don't, well-known) and the Armenian hyphen; Hebrew adds the maqaf and the ASCII quote of its abbreviations.
     */
    protected open val wordConnectors: String get() = WORD_CONNECTORS

    /**
     * Punctuation the language writes inside its words: Armenian puts its emphasis, exclamation and question marks on
     * the stressed vowel (ինչո՞ւ, կարդա՛).
     */
    open val wordMarks: String = ""

    /**
     * Letters, digits, combining marks and [wordMarks]: the vowel signs of Indic scripts are spacing marks (पढ़ा, বাড়ি),
     * drawn before their letter at times; the zero-width joiners shape letters inside a word, and a soft hyphen marks
     * where a word may break.
     */
    private fun isWordCharacter(codePoint: Int): Boolean = Character.isLetterOrDigit(codePoint) ||
        isMark(Character.getType(codePoint)) || codePoint == ZWNJ || codePoint == ZWJ || codePoint == SOFT_HYPHEN ||
        (codePoint < 0x10000 && codePoint.toChar() in wordMarks)

    /** A letter or a combining mark: the vowel sign of "डॉ." belongs to the abbreviation. */
    private fun Char.isLetterOrMark(): Boolean = isLetter() || isMark(Character.getType(this))

    private fun Char.isJoiner(): Boolean = code == ZWNJ || code == ZWJ

    private fun isMark(type: Int): Boolean =
        type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()

    /** Digits only: 1990. */
    private fun isNumber(part: String): Boolean = part.isNotEmpty() && part.all { it.isDigit() }

    /** Two capitals or more, digits among them: EU, NATO, MP3; a single one is an initial or a compound's part. */
    private fun isCapitalsAbbreviation(part: String): Boolean =
        part.count { it.isUpperCase() } >= 2 && part.all { it.isUpperCase() || it.isDigit() }

    private companion object {
        /** A Roman numeral in capitals, I to MMMCMXCIX (IV, XIV). */
        val ROMAN_NUMERAL = Regex("(?=[MDCLXVI])M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")

        const val ZWNJ = 0x200C
        const val ZWJ = 0x200D
        const val SOFT_HYPHEN = 0x00AD

        /** A soft hyphen between letters, left out of a word's spelling variant (Kan\u00ADnada as Kannada). */
        val SOFT_HYPHEN_INSIDE = Regex("(?<=[\\p{L}\\p{M}])\u00AD(?=[\\p{L}\\p{M}])")

        /** The viramas of the Brahmic scripts, Devanagari to Malayalam, which join a consonant to the next one. */
        const val VIRAMAS = "\u094D\u09CD\u0A4D\u0ACD\u0B4D\u0BCD\u0C4D\u0CCD\u0D4D"

        /** Characters inside one word: don't, rock'n'roll, well-known, and the Armenian hyphen (հայ֊ռուսական). */
        const val WORD_CONNECTORS = "'’-֊"
        const val APOSTROPHES = "'’"

        /** Characters between the parts of a hyphenated word: the hyphen, the Armenian hyphen, the Hebrew maqaf. */
        const val PART_HYPHENS = "-֊\u05BE"

        /** A hyphen, the Unicode hyphen and the soft hyphen, which marks a break inside a word. */
        const val HYPHENS = "-‐\u00AD"
        val HYPHEN_BREAK = Regex("(?<=\\p{L})[\\-‐\u00AD]\\s+(?=\\p{L})")
        val HYPHEN_JOINED = Regex("(?<=\\p{L})[\\-‐\u00AD]\\s+(?=\\p{Ll})")
        val HYPHEN_KEPT = Regex("(?<=\\p{L})([\\-‐])\\s+(?=\\p{L})")
    }
}
