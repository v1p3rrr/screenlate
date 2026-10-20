package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import kotlin.math.max

/**
 * Persian, written right to left in the Arabic script with letters of its own (پ چ ژ گ) and its own kaf and yeh (ک ی).
 * Text typed on Arabic keyboards or read with Arabic fonts gives ك ي ى instead, which the bundle and the import read as
 * ک ی ی ([headwordMarks]). The zero-width non-joiner inside words (می‌روم, کتاب‌ها) is lost in recognition and often
 * in typing, so text and headwords are compared without it, and a verb prefix or an ending written apart is tried
 * joined to its word ([earlierStartOffset]). Endings and verb prefixes are the app's own rules in the bundle.
 * Dictionaries give a Latin romanization as the reading, which the popup shows after the word; text to speech reads
 * the word itself.
 */
object PersianSupport : SpacedLanguageSupport() {
    override val language = Language.PERSIAN
    override val glyph = "پ"
    override val ocrScript = OcrScript.ARABIC
    override val wikidataId = "Q9168"
    override val languageTag = "fa"
    override val rightToLeft = true
    override val fontSample = "کتاب\u200Cها را روی میز گذاشتم؛ «چه هوای خوبی!» ۱۲۳ ۴۵۶"
    override val translationSample = "هوا امروز خوب است. می\u200Cخواهی بیرون برویم؟"
    override val audioTestWord = "کتاب"
    override val pronunciationSample = "آب → [ʔɒːb̥]"
    override val flagCountries = listOf("ir")

    /** Multiword verb forms run long: 12% of the dictionaries' headwords pass 16 characters, 2.5% pass 24. */
    override val defaultScanLength = 24

    /** The Arabic question mark ؟ ends a sentence; the Arabic comma ، and semicolon ؛ do not. */
    override val sentenceTerminators = setOf('.', '!', '?', '؟', '…', '\n')

    override val quotePairs = mapOf('«' to '»', '“' to '”', '(' to ')', '[' to ']')

    /** "ص." صفحه and "ج." جلد before a number, "ر.ک." رجوع کنید before a reference, "ق.م." قبل از میلاد. */
    override val abbreviations = setOf("ص", "ج", "ر.ک", "ق.م")

    /** Downloadable fonts made for the Arabic script, Persian ones among them. */
    override val fontSubsets = setOf(ARABIC_FONT_SUBSET)

    /** The phone's fonts for the Arabic script, which Persian shares. */
    override val systemFonts = ArabicSupport.systemFonts

    /**
     * Headwords and forms are also found with Arabic ي ك ى written as Persian ی ک ی, ۀ as ه and without the zero-width
     * non-joiner (آن‌ها under آنها). Forms in other scripts (Cyrillic Tajik ҳалол, romanized conjugations) are left out.
     */
    override val headwordMarks = HeadwordMarks(
        marks = ARABIC_VOWEL_MARKS,
        bases = ARABIC_LETTERS,
        everyHeadword = true,
        replacements = PERSIAN_LETTERS + (ZWNJ to ""),
        formsInScript = true,
    )

    /** 14% of Lingua Libre's Persian recordings are titled vowelled. */
    override val recordingMarks = ARABIC_VOWEL_MARKS

    /** A quarter of Wiktionary's Persian recordings are titled with Arabic ي and ك (`Fa-كتاب.ogg`). */
    override fun recordings(term: String, reading: String): List<RecordingTitle> =
        listOf(term, arabicLetters(term)).distinct().map { RecordingTitle(language.dictionaryCode, it) }

    /** Recognizers trained on Arabic text give its yeh, kaf and alif maqsura (ي ك ى) in Persian words. */
    override fun ocrWord(text: String): String = text.replace('ي', 'ی').replace('ك', 'ک').replace('ى', 'ی')

    /**
     * Wiktionary's transcriptions of a Persian word start with the classical one, then Dari, Hazaragi, Iranian and
     * Tajik (کتاب: /ki.ˈtaːb/ first); the Iranian ones go first: the formal one flagged 🇮🇷, the Tehrani one
     * flagged 🇮🇷TE, and the few tagged in words (`Iranian`, `Iran, various regions`).
     */
    override fun firstTranscription(tags: List<String>): Boolean =
        tags.any { it.startsWith(IRAN_FLAG) || it.startsWith("Iran") }

    /** The reading is a Latin romanization, which voices would spell out. */
    override fun speechText(term: String, reading: String): String = term

    /** wordfreq writes some words with Arabic ي and ك (اين, كه). */
    override fun frequencyWord(word: String): String = persianLetters(word)

    /** wordfreq keeps the zero-width non-joiner, which headwords may leave out (آن‌ها, آنها). */
    override fun frequencySpellings(word: String): List<String> =
        listOf(word.replace(ZWNJ, "")).filter { it != word }

    /**
     * The start of the word before the aimed one, only spaces between them, where the two are one word written apart:
     * the verb prefix می or نمی before the aimed word (می خواهم for میخواهم), or a plural or comparative ending as the
     * aimed word (کتاب ها, بزرگ ترین). The bundle tries such text joined as well.
     */
    override fun earlierStartOffset(before: String, from: String): Int {
        val joined = WORD_BEFORE.find(before)?.let { word ->
            val prefix = persianLetters(word.groupValues[1]) in VERB_PREFIXES
            if (prefix || JOINED_ENDING.containsMatchIn(from)) word.value.codePointCount(0, word.value.length) else 0
        } ?: 0
        return max(earlierStartOffset(before), joined)
    }

    private fun persianLetters(text: String): String =
        PERSIAN_LETTERS.entries.fold(text) { result, (from, to) -> result.replace(from, to) }

    private fun arabicLetters(text: String): String = text.replace('ی', 'ي').replace('ک', 'ك')
}

private const val ZWNJ = "\u200C"

private const val IRAN_FLAG = "\uD83C\uDDEE\uD83C\uDDF7"

/** Arabic letters as Persian text writes them. */
private val PERSIAN_LETTERS = mapOf("ي" to "ی", "ك" to "ک", "ى" to "ی", "ۀ" to "ه")

private val VERB_PREFIXES = setOf("می", "نمی")

/** The last word of the text before the aim and the spaces after it. */
private val WORD_BEFORE = Regex("(?<![\\p{L}\\p{M}\u200C])(\\p{L}[\\p{L}\\p{M}\u200C]*)\\s+$")

/** The aimed word is an ending written apart from its word. */
private val JOINED_ENDING = Regex("^(?:ها|های|تر|ترین)(?![\\p{L}\\p{M}\u200C])")
