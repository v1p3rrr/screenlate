package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Everything that differs between source languages. Code outside the implementations asks this class instead of
 * checking for a particular language.
 */
interface LanguageSupport {
    val language: Language

    /** A characteristic letter of the language, shown on the docked bubble. */
    val glyph: String

    /** Inserted between recognized words; empty for scripts written without spaces. */
    val wordSeparator: String

    /**
     * The gap between two words of a line, in line heights, from which on-device recognition parts them with a space
     * in a language without spaces between words, where its words are clusters (Tesseract's Thai) or phrases (its
     * Khmer); closer ones are joined. Null where words take [wordSeparator].
     */
    val ocrSpaceGap: Float? get() = null

    /**
     * Tesseract misreads much of the language's text on busy screens (photos, gradients, outlined or decorative
     * fonts), where cloud recognition reads nearly all of it; the model lists say so under its models.
     */
    val tesseractMisreadsBusyScreens: Boolean get() = false

    /** On-device recognizer to use. */
    val ocrScript: OcrScript

    /**
     * A word as OCR read it, with letters that engines take from another script written in the language's own; one
     * character for one, so character boxes stay valid. In a Cyrillic or Greek language, Latin look-alikes in a word
     * with letters of the script or of one letter take the script's letters ([LookAlikes]).
     */
    fun ocrWord(text: String): String = when (ocrScript) {
        OcrScript.CYRILLIC -> CyrillicLookAlikes.fix(text)
        OcrScript.GREEK -> GreekLookAlikes.fix(text)
        else -> text
    }

    /**
     * A page's words as OCR read them, line by line, each as [ocrWord] writes it. A language written in two scripts
     * decides by the line which one a word is in (Serbian).
     */
    fun ocrLines(lines: List<List<String>>): List<List<String>> = lines.map { line -> line.map(::ocrWord) }

    /**
     * A page's words as [ocrLines] gives them, with quotes that engines write in ASCII written as the language's: >>
     * and << at a word's start or end as » and « where [quotePairs] has them, and a " right after ! or ? as the
     * closing quote of the latest quote still open on the page. Not one for one: two characters become one.
     */
    fun ocrQuotes(lines: List<List<String>>): List<List<String>> = OcrQuotes.fix(lines, quotePairs)

    /**
     * How the lookup shortens the text after the aim when a longer match fails: by words for languages written with
     * spaces ("look up to" → "look up" → "look"), by characters otherwise (Yomitan's search resolution).
     */
    val searchResolution: SearchResolution

    /**
     * Characters from the aim point a lookup considers until the setting is changed: Yomitan's 16, more where words run
     * long.
     */
    val defaultScanLength: Int get() = 16

    /** Whether lines run from right to left. */
    val rightToLeft: Boolean get() = false

    /**
     * Linking elements between the parts of a compound written as one word (German -s- in Arbeitszimmer), lowercase:
     * a word without an entry of its own is shown as its parts. Null for a language whose words are not split.
     */
    val compoundLinks: List<String>? get() = null

    /**
     * Whether nouns are written with a capital letter (German): a part of a compound, looked up in lowercase, shows its
     * capitalized entries first (lampe: the noun Lampe before the verb lampen).
     */
    val capitalizedNouns: Boolean get() = false

    /** Characters that may end a sentence, for `{sentence}` and cloze markers; [endsSentence] has the final say. */
    val sentenceTerminators: Set<Char>

    /** Whether the character at [index] of [text] ends a sentence. */
    fun endsSentence(text: String, index: Int): Boolean = text[index] in sentenceTerminators

    /** Opening to closing brackets and quotes; a terminator inside them does not end the sentence. */
    val quotePairs: Map<Char, Char>

    /** Anki markers that exist only for this language (e.g. pitch accent), in addition to the common ones. */
    val ankiMarkers: List<String>

    /**
     * Whether Anki's furigana markers (`{furigana}`, `{sentence-furigana}` and their plain forms, Yomitan's names) are
     * offered: the language's readings go over its words or into them (Japanese kana, Chinese pinyin, Russian stress
     * marks). Elsewhere they would repeat the word; a field that has them still gets them.
     */
    val furiganaMarkers: Boolean get() = false

    /**
     * Anki markers ([ankiMarkers]) that spell the word in one of [scriptTags], to that script's tag:
     * `{expression-traditional}` for a note type with a field for each script.
     */
    val scriptMarkers: Map<String, String> get() = emptyMap()

    /**
     * Anki markers of the language's own text in a language written right to left: the word, the sentence and its
     * cloze parts. Suggested templates and the marker picker put them in `<span dir="rtl">`, so that a card laid out
     * left to right shows them in order; meanings and transcriptions stay as they are.
     */
    val rightToLeftMarkers: Set<String> get() = if (rightToLeft) RIGHT_TO_LEFT_TEXT_MARKERS else emptySet()

    /**
     * Field names of note types made for the language, by the marker such a field is pre-filled with (Pinyin →
     * `reading`); they come before the names every language shares.
     */
    val ankiFieldNames: Map<String, List<String>> get() = emptyMap()

    /** Default audio sources, as `AudioSourceType` names, in priority order. */
    val defaultAudioSources: List<String>

    /** Wikidata item of the language, which Lingua Libre's file titles name (`LL-Q5287 (jpn)-Speaker-猫.wav`). */
    val wikidataId: String

    /** The Wikidata items whose Lingua Libre recordings fit the language, best first: its own. */
    val linguaLibreItems: List<String> get() = listOf(wikidataId)

    /** The spelling Lingua Libre's recordings of [term] are titled by: the word itself. */
    fun linguaLibreTitle(term: String): String = term

    /**
     * Vowel marks recording titles may write where the word has none (`Ar-كِتَابٌ.oga` for كتاب). Titles that differ
     * from the word only by them are found too; those with the word itself come first, then those with its vowelled
     * reading, then the rest, which may be another word of the same letters.
     */
    val recordingMarks: String get() = ""

    /**
     * The spelling Wiktionary's recordings of a word ([term] read as [reading]) are titled by: the word itself, or its
     * pinyin for Chinese (`Zh-péngyou.ogg`).
     */
    fun recordingTitle(term: String, reading: String): String = term

    /**
     * Wiktionary's recordings to look for, best first, by the language code their titles start with and the spelling
     * they are titled by: the dictionaries' code and [recordingTitle], unless Wiktionary files the language's
     * recordings under other codes too (Croatian takes Serbian `Sr-кућа.flac`).
     */
    fun recordings(term: String, reading: String): List<RecordingTitle> =
        listOf(RecordingTitle(language.dictionaryCode, recordingTitle(term, reading)))

    /**
     * The language codes system locales and translation services give this language and those that read the same
     * dictionaries: Croatian, Serbian, Bosnian and Montenegrin for Croatian and Serbian. A translation into one of them
     * would repeat the text.
     */
    val localeLanguages: Set<String> get() = setOf(language.dictionaryCode)

    /**
     * The translation services' tag for [text] where the text decides it (Serbian in Latin letters: sr-Latn); null for
     * the language's own.
     */
    fun translationTag(text: String): String? = null

    /** What text to speech reads for a word: its reading where a voice reads it as the word (kana), else the word. */
    fun speechText(term: String, reading: String): String = reading.ifEmpty { term }

    /** BCP 47 tag of the language, for `lang` attributes and locale-dependent glyph forms. */
    val languageTag: String

    /** The language's name in [locale] as running text writes it (lowercase in Russian); see `Language.displayName`. */
    fun nameIn(locale: Locale): String = Locale.forLanguageTag(language.code).getDisplayLanguage(locale)

    /** BCP 47 tag of the text-to-speech voice: the language's code unless the voices know it by another. */
    val speechTag: String get() = language.code

    /** What stands for the language where its flag cannot (e-ink): its code in capitals. */
    val codeLabel: String get() = language.code.uppercase(Locale.ROOT)

    /** The phone's fonts for the language, used by the lookup page unless the user picks another font. */
    val systemFonts: SystemFonts

    /**
     * Google Fonts subsets (`latin`, `latin-ext`, `vietnamese`, `cyrillic`) a font needs for the language's letters: a
     * downloadable font made for no language in particular is offered for every language whose subsets it has. Empty:
     * only fonts made for this language are offered (Japanese, Chinese and Korean glyph forms differ).
     */
    val fontSubsets: Set<String> get() = emptySet()

    /**
     * Letters beyond a-z the language needs of a font offered by [fontSubsets], in lower case (their capitals are needed
     * too) and capitals without a lower case of their own (Turkish İ). A font whose listed letters lack one of them is
     * not offered, though its subsets match: Atkinson Hyperlegible Next declares Latin Extended without ō.
     */
    val fontLetters: String get() = ""

    /** Sample text for font previews, with characters whose forms differ between fonts or regions. */
    val fontSample: String

    /** A short sentence for the translation services' test. */
    val translationSample: String

    /** A common word for the audio sources' test. */
    val audioTestWord: String

    /**
     * Countries whose round flag may stand for the language (ISO 3166-1 alpha-2, lower case), the default first; the
     * Languages screen offers the others. A language with a flag of its own and no country lists `lang_<code>` (Latin).
     * Each has a `flag_<country>` drawable.
     */
    val flagCountries: List<String>

    /** Regions of Wiktionary recordings in their default order; empty where the file titles name none. */
    val audioRegions: List<AudioRegion> get() = emptyList()

    /**
     * Accent names in the transcription tags of Wiktionary dictionaries (`General-American`) and the regions they
     * stand for (`us`); flag emoji need no entry (see [TranscriptionRegions]).
     */
    val transcriptionRegionNames: Map<String, String> get() = emptyMap()

    /**
     * A word with its transcription, the example that explains pronunciation dictionaries (`water → /ˈwɔːtər/`); null
     * where they hold something else (pitch accents for Japanese).
     */
    val pronunciationSample: String? get() = null

    /**
     * Whether a phonetic transcription with [tags] goes before the others of its word, which keep the dictionary's
     * order: Hebrew's Modern Israeli one before the Biblical and Tiberian ones Wiktionary lists first for some words.
     */
    fun firstTranscription(tags: List<String>): Boolean = false

    /**
     * Whether a dictionary word may start at the beginning of [text] (the text from the aim point on), and which
     * matches count; null when there is nothing to look up.
     *
     * @param latinAsNative Latin text is converted by [fromLatin] before the lookup.
     */
    fun lookupStart(text: String, latinAsNative: Boolean): LookupStart?

    /**
     * How many characters (code points) before the aimed one the lookup starts, so a word the language reads as a
     * whole is looked up from its beginning; [before] is the text before the aimed character, [aimed] that character.
     */
    fun wordStartOffset(before: String, aimed: String): Int

    /**
     * [wordStartOffset] with the text [after] the aimed character as well, which a language divided into words by a
     * segmenter needs to tell which word the aimed character is in ([SegmentedLanguageSupport]).
     */
    fun wordStartOffset(before: String, aimed: String, after: String): Int = wordStartOffset(before, aimed)

    /**
     * How many code points before the aimed word the lookup starts as well, for a language that writes the syllables
     * of a word apart (Vietnamese "học sinh"): a word found from an earlier start that reaches the aimed one comes
     * before the aimed word's own results. [before] is the text before the aimed word; 0 when lookups start only there.
     */
    fun earlierStartOffset(before: String): Int = 0

    /** [earlierStartOffset] with the text [from] the aimed word on as well, which a word segmenter reads too. */
    fun earlierStartOffset(before: String, from: String): Int = earlierStartOffset(before)

    /**
     * UTF-16 indexes of [text] before [end] where a lookup that starts at the words before the aimed one
     * ([earlierStartOffset]) starts as well: at 0 and after each run of spaces, or where a segmenter starts a word.
     */
    fun wordStarts(text: String, end: Int): List<Int> = (0 until end).filter { index ->
        !Character.isSpaceChar(text[index]) && (index == 0 || Character.isSpaceChar(text[index - 1]))
    }

    /**
     * UTF-16 indexes of [text] after [from] up to [to] where a lookup starts as well when nothing found from the aimed
     * word's start at [from] reaches the aimed character at [to]: inside a word a segmenter joined from words of a
     * dictionary (Khmer ក្រុងភ្នំពេញ, of which a dictionary may have only ក្រុង and ភ្នំពេញ). Empty where the aimed
     * word is the whole lookup.
     */
    fun innerStarts(text: String, from: Int, to: Int): List<Int> = emptyList()

    /**
     * UTF-16 indexes of [text] after [from] up to [to] where the parts of a hyphenated word start, [from] being the
     * aimed word's start and [to] the aimed character: a tap on a later part shows the whole word when a dictionary has
     * it, then the tapped part (state-owned tapped on owned shows owned, not state). Empty when no hyphen comes before
     * the aimed character, and in languages written without spaces.
     */
    fun partStarts(text: String, from: Int, to: Int): List<Int> = emptyList()

    /**
     * [text] with the letters OCR and keyboards lose written as the ones the headwords are keyed with for them
     * ([HeadwordMarks.replacements]: Kazakh қ as к), when no word was found exactly as written in more than [found]
     * UTF-16 characters, fewer than its first word, and that word writes such a letter; null otherwise. Its results
     * as long as those found as written or longer are shown after them when they keep the letters the text writes
     * ([keepsWrittenLetters]): "Қазакстан" (қ read as к) finds Қазақстан, Kyrgyz "Түштук" (found as түшүү through an
     * ending) finds түштүк too, while "оң", found as written, shows only оң.
     */
    fun keyedSpelling(text: String, found: Int): String? = null

    /** Whether [headword] has the letter [text] writes wherever [text] writes one that [keyedSpelling] replaces. */
    fun keepsWrittenLetters(text: String, headword: String): Boolean = true

    /**
     * Of the [lengths] (UTF-16) of the matches found from the start of [text], those shown: every one, unless a
     * segmenter divides the language's words ([SegmentedLanguageSupport]).
     */
    fun shownMatchLengths(text: String, lengths: Set<Int>): Set<Int> = lengths

    /** Other spellings of [text] that are looked up as well; the original is always looked up. */
    fun spellingVariants(text: MappedText): List<MappedText>

    /** Converts a transliteration in Latin letters into the language's script; null if there is nothing to convert. */
    fun fromLatin(text: MappedText): MappedText?

    /** Characters of a matched word that get entries of their own below the results (kanji for Japanese). */
    fun singleCharacterEntries(matched: String): List<String>

    /** Whether [singleCharacterEntries] gives any, which the lookup settings offer to turn off. */
    val hasCharacterEntries: Boolean get() = false

    /** Whether [fromLatin] converts anything, which the lookup settings offer to turn on (romaji for Japanese). */
    val convertsLatin: Boolean get() = false

    /**
     * The first character of [text] when kanji dictionaries describe such characters (a kanji for Japanese), for the
     * kanji entry shown when no word is found there; null otherwise.
     */
    fun characterEntry(text: String): String?

    /**
     * BCP 47 tags of the scripts the language is written in, where it has several (Simplified and Traditional
     * Chinese); empty otherwise. Each result of a lookup is tagged with one of them.
     */
    val scriptTags: List<String> get() = emptyList()

    /** The script ([scriptTags]) [text] is written in; null where it reads the same in each. */
    fun scriptOf(text: String): String? = null

    /**
     * [text] written in the script with [tag] ([scriptTags]), one spelling per likely conversion, the most likely
     * first; empty when nothing in it changes.
     */
    fun inScript(text: MappedText, tag: String): List<MappedText> = emptyList()

    /**
     * Spellings of a word of wordfreq's list that the list leaves out, which share its rank: Traditional characters for
     * wordfreq's Simplified Chinese. Empty where the list has every spelling.
     */
    fun frequencySpellings(word: String): List<String> =
        scriptTags.flatMap { tag -> inScript(MappedText.identity(word), tag).map { it.text } }

    /** A word of wordfreq's list as dictionaries spell it: wordfreq writes the Greek final sigma as σ (τησ for της). */
    fun frequencyWord(word: String): String = word

    /** The script for words written alike in each that an interface language ([locale]) suggests. */
    fun defaultScriptTag(locale: Locale): String? = scriptTags.firstOrNull()

    /**
     * Tags of form-of entries that only spell a word in another script (Wiktionary's `Simplified-Chinese`); such a
     * result is shown under the spelling that was read.
     */
    val spellingFormTags: Set<String> get() = emptySet()

    /**
     * Tags of form-of entries that lookups do not go through: Wiktionary's Vietnamese dictionary lists "cái" as the
     * `classifier` form of hundreds of nouns. A result keeps its other chains of steps and is left out without them.
     */
    val ignoredFormTags: Set<String> get() = emptySet()

    /**
     * Whether results under [expression], a dictionary's headword, are shown: Wiktionary links some Romanian words to
     * their Moldovan Cyrillic spellings, which Romanian leaves out.
     */
    fun showsHeadword(expression: String): Boolean = true

    /**
     * Whether a word's results reached through the inflection rules are left out when the same text also finds results
     * without them: the dictionary lists the forms of most words itself, and the rules are there for the words whose
     * entries list none (Romanian consiliul → consiliu, where casă has its own entry and is not cas). A result reached
     * both ways shows only its chains without the rules (Finnish taloon: talo's illative row, not -on).
     */
    val inflectionRulesOnlyWithoutEntry: Boolean get() = false

    /**
     * Tags of the dictionaries' form-of entries that stand for one of the language's inflection rules, with that rule's
     * id: Wiktionary's Welsh `soft` and French-edition `lenition` rows are soft mutations, which the rules undo too. A
     * result shows such a row under the rule's name, once, and a row whose lemma is a single letter (Welsh fara → b)
     * is left out.
     */
    val formTagRules: Map<String, String> get() = emptyMap()

    /**
     * Words of form-of tags that mean another tag's word in this language, named as that one: Hungarian `definite` on
     * a verb form is the definite conjugation, which other rows of the same dictionary call `object-definite`.
     */
    val formTagAliases: Map<String, String> get() = emptyMap()

    /**
     * Whether a word's results found through a headword's spelling without marks ([HeadwordMarks.everyHeadword]) are
     * left out when the same text finds a headword as written: Welsh ty finds tŷ, twr finds only twr.
     */
    val respelledOnlyWithoutEntry: Boolean get() = false

    /**
     * The language whose term dictionaries lookups also search where the language's own find no entry for the text:
     * Wiktionary's Slovak dictionaries lack many common words that Czech writes the same (v, už, však), so Slovak reads
     * the Czech dictionaries the user has, their entries marked as Czech. Null for the others.
     */
    val fallbackLanguage: Language? get() = null

    /**
     * Stress marks that the words of form-of rows in the language's dictionaries carry and running text leaves out: at
     * import a form is also found without them, and the lemmas forms name are stored without them; or, with
     * [HeadwordMarks.everyHeadword], marks headwords carry (the Greek tonos, the Hindi nukta). Null where words carry
     * none.
     */
    val headwordMarks: HeadwordMarks? get() = null

    /** A dictionary's reading as entries show it, without notations it adds ("xíng (xing²)" → "xíng"). */
    fun displayReading(reading: String): String = reading

    /**
     * The form of a reading ([displayReading]) under which results of one word join, as Yomitan's reading normalizer
     * groups them: pinyin "nǐ hǎo" and "nǐhǎo" are one reading.
     */
    fun normalizeReading(reading: String): String = reading

    /** Whether a typed search also finds words by their reading in Latin letters (pinyin), see [readingKey]. */
    val readingSearch: Boolean get() = false

    /**
     * What a typed reading and a dictionary's reading ([displayReading]) of the same word share for [readingSearch]:
     * for pinyin, its letters without tones, spaces or apostrophes. Null when [reading] is not one.
     */
    fun readingKey(reading: String): String? = null

    /**
     * How well what [query] types beyond the [readingKey] agrees with [reading] of the same key: for pinyin, one point
     * for each tone and syllable break that matches, one off for each that does not; 0 when the query has none.
     */
    fun readingMatch(query: String, reading: String): Int = 0
}

/** Which matches a lookup accepts. */
sealed interface LookupStart {
    /** Any match from the start of the text, the longest first. */
    data object Any : LookupStart

    /** Only matches of exactly [length] UTF-16 characters: a word that counts only as a whole. */
    data class Whole(val length: Int) : LookupStart
}

enum class FontStyle { SANS, SERIF }

/**
 * @property sans names of the phone's sans-serif font for the language as CSS `local()` takes them (PostScript or
 * full names), most specific first.
 * @property serif the same for the serif font.
 * @property unicodeRange CSS `unicode-range` of the language's script, so other text keeps the default font.
 * @property aliases font names common on other systems (e.g. Windows) that stand for these fonts in user CSS.
 */
data class SystemFonts(
    val sans: List<String>,
    val serif: List<String>,
    val unicodeRange: String,
    val aliases: Map<String, FontStyle>,
)

enum class SearchResolution { WORD, LETTER }

/**
 * Combining [marks] dropped from a word where they follow one of [bases] (lowercase letters), in the word's decomposed
 * form: the acute of Serbo-Croatian ć stays, as it follows no vowel.
 *
 * @property everyHeadword headwords carry the marks and lemmas name headwords as written: a headword with definitions
 * is also found without them, and lemmas keep them.
 * @property replacements characters that running text writes as others (the Hindi candrabindu as anusvara, the
 * Arabic ي of a Persian headword as ی) or leaves out (the Persian zero-width non-joiner, replaced by nothing): a
 * word is also found with them replaced, with its [marks] and without them.
 * @property furtherReplacements replacements made on top of [replacements] for one more spelling: Arabic headwords are
 * found with final ى and ة written ي and ه, and also with their hamza alefs written as a bare alif.
 * @property lemmasAsWritten lemmas name headwords with their marks and keep them; otherwise they lose them (Arabic
 * forms name vowelled lemmas of unvowelled headwords).
 * @property formsInScript forms are written in the script of [bases]: a form without one of its letters is dropped
 * at import (the romanized forms Wiktionary's Arabic exports give).
 * @property markBases further marks, each with the letters it is dropped after in place of [bases]: Lithuanian drops
 * the dot above after i (the soft dot of an accented i), not after e (ė).
 * @property letterMarks marks of the language's letters (ą č ė), dropped after [letterBases] once [marks] are, for one
 * more key of each form (and, with [everyHeadword], each headword): text written without them finds the words with
 * them, the exact spelling first.
 */
data class HeadwordMarks(
    val marks: String,
    val bases: String,
    val everyHeadword: Boolean = false,
    val replacements: Map<String, String> = emptyMap(),
    val furtherReplacements: Map<String, String> = emptyMap(),
    val lemmasAsWritten: Boolean = everyHeadword,
    val formsInScript: Boolean = false,
    val markBases: Map<String, String> = emptyMap(),
    val letterMarks: String = "",
    val letterBases: String = "",
)

/** The markers of [LanguageSupport.rightToLeftMarkers] every right-to-left language has. */
internal val RIGHT_TO_LEFT_TEXT_MARKERS = setOf("expression", "sentence", "cloze-prefix", "cloze-body", "cloze-suffix")

/** A Wiktionary recording title to look for: `Sr-кућа.flac` has the [prefix] sr and the [title] кућа. */
data class RecordingTitle(val prefix: String, val title: String)

/** On-device OCR models; each one covers a script family. */
enum class OcrScript {
    LATIN, ARABIC, ARMENIAN, BENGALI, CHINESE, CYRILLIC, DEVANAGARI, GEORGIAN, GREEK, HEBREW, JAPANESE, KHMER, KOREAN,
    THAI,
}

val Language.support: LanguageSupport
    get() = when (this) {
        Language.JAPANESE -> JapaneseSupport
        Language.ENGLISH -> EnglishSupport
        Language.CHINESE -> ChineseSupport
        Language.KOREAN -> KoreanSupport
        Language.SPANISH -> SpanishSupport
        Language.FRENCH -> FrenchSupport
        Language.GERMAN -> GermanSupport
        Language.ITALIAN -> ItalianSupport
        Language.PORTUGUESE -> PortugueseSupport
        Language.POLISH -> PolishSupport
        Language.TURKISH -> TurkishSupport
        Language.LATVIAN -> LatvianSupport
        Language.DUTCH -> DutchSupport
        Language.CZECH -> CzechSupport
        Language.INDONESIAN -> IndonesianSupport
        Language.MALAY -> MalaySupport
        Language.FILIPINO -> FilipinoSupport
        Language.VIETNAMESE -> VietnameseSupport
        Language.RUSSIAN -> RussianSupport
        Language.CROATIAN -> CroatianSupport
        Language.SERBIAN -> SerbianSupport
        Language.UKRAINIAN -> UkrainianSupport
        Language.BELARUSIAN -> BelarusianSupport
        Language.GREEK -> GreekSupport
        Language.HINDI -> HindiSupport
        Language.BENGALI -> BengaliSupport
        Language.GEORGIAN -> GeorgianSupport
        Language.ARMENIAN -> ArmenianSupport
        Language.THAI -> ThaiSupport
        Language.KHMER -> KhmerSupport
        Language.ARABIC -> ArabicSupport
        Language.PERSIAN -> PersianSupport
        Language.HEBREW -> HebrewSupport
        Language.KAZAKH -> KazakhSupport
        Language.KYRGYZ -> KyrgyzSupport
        Language.SWEDISH -> SwedishSupport
        Language.NORWEGIAN -> NorwegianSupport
        Language.DANISH -> DanishSupport
        Language.ROMANIAN -> RomanianSupport
        Language.LATIN -> LatinSupport
        Language.LITHUANIAN -> LithuanianSupport
        Language.SLOVAK -> SlovakSupport
        Language.SLOVENIAN -> SlovenianSupport
        Language.ESPERANTO -> EsperantoSupport
        Language.ALBANIAN -> AlbanianSupport
        Language.SWAHILI -> SwahiliSupport
        Language.WELSH -> WelshSupport
        Language.IRISH -> IrishSupport
        Language.SCOTTISH_GAELIC -> GaelicSupport
        Language.FINNISH -> FinnishSupport
        Language.HUNGARIAN -> HungarianSupport
        Language.ESTONIAN -> EstonianSupport
        Language.AZERBAIJANI -> AzerbaijaniSupport
    }

/**
 * The dictionary codes lookups in this language read: its own ([Language.dictionaryCode]) and its fallback language's
 * ([LanguageSupport.fallbackLanguage]); Slovak reads sk and cs.
 */
val Language.lookupDictionaryCodes: List<String>
    get() = listOfNotNull(dictionaryCode, support.fallbackLanguage?.dictionaryCode)
