package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.LookupStart
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.core.common.language.SearchResolution
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.ReadingIndex
import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Entry point for lookups: loads the enabled dictionaries on first use and applies the language's spelling variants,
 * the lookup settings (romaji, result limit, single kanji entries) and the sort settings.
 */
@Singleton
class DictionaryLookup @Inject constructor(
    private val repository: DictionaryRepository,
    private val engine: DictionaryEngine,
    private val lookupSettings: LookupSettingsRepository,
    private val imports: Provider<DictionaryImports>,
) {
    // Asked for every scan or shown word; kept until the dictionaries change.
    private val cachedStyles = PerGeneration<List<DictionaryStyle>>()
    private val cachedTagNotes = PerGeneration<List<DictionaryTagNotes>>()
    private val cachedFrequencyModes = PerGeneration<Map<String, String>>()
    private val cachedTranscriptionTags = PerGeneration<Map<String, TranscriptionTags>>()
    private val cachedReadingIndexes = PerGeneration<Pair<Language, List<ReadingIndex>>>()

    /**
     * @param scanLength characters of [text] to consider; the scan length setting when null.
     * @param primaryReading terms with this reading come first, as for Yomitan's `primary_reading` links.
     * @param extraEntries add the single kanji entries below the results when that setting is on.
     * @param aimedAt UTF-16 index in [text] where the aimed word starts, after the words before it that the language
     *   looks up from as well (`LanguageSupport.earlierStartOffset`); see [fromEarlierStarts].
     * @param aimedCharAt UTF-16 index in [text] of the aimed character, at or after [aimedAt]: when no word found
     *   covers it, the lookup starts inside the aimed word as well; see [reachingAimedCharacter]. After a hyphen in
     *   the aimed word, the lookup starts at the aimed part too; see [withHyphenatedParts].
     * @param fromOcr [text] was recognized in a screenshot ([LookupOptions.fromOcr]); a word that finds nothing is also
     *   tried in the spellings of letters engines misread ([misreadMatches]).
     */
    suspend fun lookup(
        text: String,
        language: Language,
        scanLength: Int? = null,
        primaryReading: String? = null,
        extraEntries: Boolean = true,
        aimedAt: Int = 0,
        aimedCharAt: Int = aimedAt,
        fromOcr: Boolean = false,
    ): List<LookupResult> {
        if (text.isBlank()) return emptyList()
        val settings = lookupSettings.current(language)
        val support = language.support
        support.lookupStart(text.substring(aimedAt), latinAsNative = settings.romaji) ?: return emptyList()
        return repository.withLookup(language) { prepared ->
            val limit = settings.maxResults.takeIf { it > 0 } ?: Int.MAX_VALUE
            val options = prepared.options.copy(
                scanLength = scanLength ?: settings.scanLength,
                primaryReading = primaryReading,
                maxResults = limit,
                language = language,
                fromOcr = fromOcr,
            )
            val engineOptions = options.copy(maxResults = engineLimit(limit, prepared.termDictionaries.size))
            val script = settings.script?.takeIf { settings.fixedScript }

            // [part]: the entries of a compound's part.
            suspend fun shown(found: List<LookupResult>, start: LookupStart, part: Boolean = false): List<LookupResult> = YomitanSorter
                .sort(found, options, prepared.termDictionaries)
                .let { if (part && support.capitalizedNouns) it.capitalizedFirst() else it }
                .filter { start !is LookupStart.Whole || it.matched.length == start.length }
                .mapNotNull { it.shownBy(support) }
                .mergeSameWords(prepared.termDictionaries, support)
                .let { if (script != null) it.inScript(script, support, prepared.termDictionaries) else it }
                .take(limit)
                .map { it.withSortFrequencyFirst(options.frequencyDictionary) }
                .withTranscriptions(support)

            suspend fun from(source: String): List<LookupResult> {
                val start = support.lookupStart(source, latinAsNative = settings.romaji) ?: return emptyList()
                val written = LookupVariants.of(source, settings, support).flatMap { variant ->
                    engine.lookup(variant.text, engineOptions).fromVariant(source, variant, support)
                }.mapNotNull { it.shownBy(support) }
                val candidates = preferringOwn(written, prepared.fallbackDictionaries, support.fallbackLanguage?.code) { found, kept ->
                    found + keyedMatches(source, found, support) { kept(engine.lookup(it, engineOptions)) }
                }
                val lengths = support.shownMatchLengths(source, candidates.mapTo(HashSet()) { it.matched.length })
                val found = candidates.filter { it.matched.length in lengths }
                val whole = shown(found, start).ifEmpty {
                    if (!fromOcr) return@ifEmpty emptyList()
                    misreadMatches(source, support, { engine.lookup(it, engineOptions) }) { shown(it, start) }
                }
                return support.compoundLinks?.let { links ->
                    compoundParts(source, whole, links) { part ->
                        // Every word the rest starts with, not only whole words.
                        val partOptions = engineOptions.copy(
                            scanLength = part.codePointCount(0, part.length),
                            searchResolution = SearchResolution.LETTER,
                        )
                        engine.lookup(part, partOptions).map { it.asShown(support) }
                    }
                }?.map { shown(it, start, part = true) }?.markedAsCompound() ?: whole
            }

            val fromWord = if (aimedAt == 0) {
                from(text)
            } else {
                fromEarlierStarts(text, aimedAt, support.wordStarts(text, aimedAt)) { from(text.substring(it)) }.take(limit)
            }
            val inner = support.innerStarts(text, aimedAt, aimedCharAt)
            val reaching = if (inner.isEmpty()) {
                fromWord
            } else {
                reachingAimedCharacter(fromWord, aimedCharAt, inner) { from(text.substring(it)) }.take(limit)
            }
            val parts = support.partStarts(text, aimedAt, aimedCharAt)
            val results = if (parts.isEmpty()) {
                reaching
            } else {
                withHyphenatedParts(text, reaching, aimedCharAt, parts) { from(text.substring(it)) }.take(limit)
            }
            val all = if (!extraEntries || !settings.singleKanji) {
                results
            } else {
                results + singleCharacterEntries(results, language, options, prepared.termDictionaries, script != null)
            }
            val context = support.scriptOf(text)
            all.map { it.copy(lang = scriptTag(it.term.expression, context, settings, support)) }
        }
    }

    /**
     * A lookup of a whole query (the search field, a dictionary link) rather than of text at the aim point: the scan
     * covers the query, up to [LookupSettings.MAX_SCAN_LENGTH] characters, instead of the scan length setting. In a
     * language with [LanguageSupport.readingSearch], the words whose reading the query spells (pinyin) follow.
     */
    suspend fun lookupQuery(text: String, language: Language, primaryReading: String? = null): List<LookupResult> {
        val found = lookup(text, language, scanLength = queryScanLength(text), primaryReading = primaryReading)
        val support = language.support
        val query = text.trim()
        val key = if (support.readingSearch) support.readingKey(query) else null
        if (key == null) return found
        // The engine finds a word whose reading is typed exactly as the dictionary writes it (匙 for "shi"); the reading
        // search finds it as well, in its own order.
        val spelled = found.filter { it.term.expression == it.matched }
        val shown = spelled.mapTo(HashSet()) { it.term.expression to it.term.reading }
        return spelled + readingMatches(query, key, language).filter { (it.term.expression to it.term.reading) !in shown }
    }

    /**
     * [expression] read as [reading] in the script [tag] (`LanguageSupport.scriptTags`), checked against the term
     * dictionaries where a character has several forms; see [spelling].
     */
    suspend fun inScript(expression: String, reading: String, tag: String, language: Language): String {
        val support = language.support
        return repository.withLookup(language) { prepared ->
            spelling(expression, reading, tag, support) { candidate ->
                val options = prepared.options.copy(scanLength = candidate.codePointCount(0, candidate.length), language = language)
                engine.lookup(candidate, options).map { it.asShown(support) }
            }
        }
    }

    /**
     * Words whose reading has the [key] of [query]: the words the reading indexes list for it, each looked up in the
     * chosen script, as typed pinyin has none of its own; the tones typed in the query rank first.
     */
    private suspend fun readingMatches(query: String, key: String, language: Language): List<LookupResult> {
        val settings = lookupSettings.current(language)
        val support = language.support
        return repository.withLookup(language) { prepared ->
            val started = System.currentTimeMillis()
            val lists = readingIndexes(language).map { it.find(key) }
            val words = readingWords(lists, settings.script, support).take(MAX_READING_WORDS)
            val limit = settings.maxResults.takeIf { it > 0 } ?: Int.MAX_VALUE
            val options = prepared.options.copy(maxResults = limit, language = language)
            val engineLimit = engineLimit(limit, prepared.termDictionaries.size)

            suspend fun entriesOf(word: String): List<LookupResult> {
                val engineOptions = options.copy(scanLength = word.codePointCount(0, word.length), maxResults = engineLimit)
                return engine.lookup(word, engineOptions)
                    .map { it.asShown(support) }
                    .filter { it.term.expression == word && support.readingKey(it.term.reading) == key }
                    // Each word matches the whole query, which the page marks as read.
                    .map { it.copy(matched = query) }
            }

            val found = words.flatMap { word ->
                entriesOf(word.spelling).ifEmpty { if (word.original != word.spelling) entriesOf(word.original) else emptyList() }
            }
            val script = settings.script
            val results = YomitanSorter.sort(found, options, prepared.termDictionaries)
                .sortedByDescending { support.readingMatch(query, it.term.reading) }
                .mergeSameWords(prepared.termDictionaries, support)
                .let { if (script != null) it.inScript(script, support, prepared.termDictionaries) else it }
                .take(limit)
                .map { it.withSortFrequencyFirst(options.frequencyDictionary) }
                .withTranscriptions(support)
                .map { it.copy(lang = scriptTag(it.term.expression, null, settings, support)) }
            Log.d(
                TAG,
                "Reading search: ${lists.sumOf { it.size }} words listed, ${words.size} looked up, ${results.size} shown " +
                    "in ${System.currentTimeMillis() - started} ms",
            )
            results
        }
    }

    /** The reading indexes of [language]'s dictionaries, kept for the generation of the language last asked. */
    private suspend fun readingIndexes(language: Language): List<ReadingIndex> {
        val cached = cachedReadingIndexes.get(repository.generation) { language to repository.readingIndexes(language) }
        return if (cached.first == language) cached.second else repository.readingIndexes(language)
    }

    /**
     * Entries for the characters of the longest match that the language gives entries of their own (kanji), as
     * words of a term dictionary; terms already shown are skipped. With [fixedScript] the characters are those of the
     * word as shown, which may be the match in the other script.
     */
    private suspend fun singleCharacterEntries(
        results: List<LookupResult>,
        language: Language,
        options: LookupOptions,
        dictionaryOrder: List<String>,
        fixedScript: Boolean,
    ): List<LookupResult> {
        val result = results.maxByOrNull { it.matched.length } ?: return emptyList()
        val respelled = fixedScript && result.trace.isEmpty() && result.term.expression.length == result.matched.length
        val longest = if (respelled) result.term.expression else result.matched
        val shown = results.mapTo(mutableSetOf()) { it.term.expression to it.term.reading }
        val support = language.support
        return support.singleCharacterEntries(longest).flatMap { character ->
            val engineOptions = options.copy(
                scanLength = 1,
                primaryReading = null,
                maxResults = engineLimit(options.maxResults, dictionaryOrder.size),
            )
            val entries = engine.lookup(character, engineOptions)
                .map { it.asShown(support) }
                .filter { it.term.expression == character && it.deinflected == character }
                .filter { shown.add(it.term.expression to it.term.reading) }
            YomitanSorter.sort(entries, options, dictionaryOrder).take(options.maxResults)
        }
    }

    /** See [Transcriptions]; the tags are read only when a result has transcriptions. */
    private suspend fun List<LookupResult>.withTranscriptions(support: LanguageSupport): List<LookupResult> {
        if (none { result -> result.term.pitches.any { it.transcriptions.isNotEmpty() } }) return this
        val tags = cachedTranscriptionTags.get(repository.generation) { repository.transcriptionTags() }
        return map { Transcriptions.prepare(it, tags, support.transcriptionRegionNames, support::firstTranscription) }
    }

    /** The lookup settings in effect for [language]. */
    suspend fun settings(language: Language): LookupSettings = lookupSettings.current(language)

    fun settingsUpdates(language: Language): Flow<LookupSettings> = lookupSettings.settings(language)

    /** The same list until the dictionaries change, so callers can skip work for an unchanged one. */
    suspend fun styles(language: Language): List<DictionaryStyle> = repository.withLookup(language) {
        cachedStyles.get(repository.generation) { engine.styles() }
    }

    /** Reads an image from the dictionary set of the page or note's captured [language]. */
    suspend fun media(dictionary: String, path: String, language: Language): ByteArray? = repository.withLookup(language) {
        engine.media(dictionary, path)
    }

    /** Tag descriptions of the enabled dictionaries, shown when a tag in the popup is tapped. */
    suspend fun tagNotes(): List<DictionaryTagNotes> = cachedTagNotes.get(repository.generation) { repository.tagNotes() }

    /** Entries for one character from the enabled kanji dictionaries; empty when there are none. */
    suspend fun kanji(character: String, language: Language): KanjiResult {
        val found = repository.withLookup(language) { engine.kanji(character) }
        if (language.support.scriptTags.isEmpty()) return found
        return found.copy(lang = scriptTag(character, null, settings(language), language.support))
    }

    /**
     * The kanji entry for the first character of [text], shown instead of results when no word is found there, as in
     * Yomitan; null when the language gives that character no entry or no enabled kanji dictionary has it.
     */
    suspend fun characterEntry(text: String, language: Language): KanjiResult? {
        val character = language.support.characterEntry(text) ?: return null
        return kanji(character, language).takeIf { it.entries.isNotEmpty() }
    }

    /** `rank-based` or `occurrence-based` for each enabled frequency dictionary that declares it, by title. */
    suspend fun frequencyModes(): Map<String, String> = cachedFrequencyModes.get(repository.generation) {
        repository.getAll()
            .filter { it.enabled }
            .mapNotNull { entity -> entity.frequencyMode?.let { entity.title to it } }
            .toMap()
    }

    /** Whether lookups in [language] search any dictionary with definitions (enabled, for the language, with files). */
    suspend fun hasTermDictionaries(language: Language): Boolean = repository.hasTermDictionaries(language)

    /** Why lookups in [language] search no dictionary with definitions; null when they search one. */
    suspend fun noTermDictionary(language: Language): NoTermDictionary? = noTermDictionary(
        hasTermDictionaries = repository.hasTermDictionaries(language),
        registryEmpty = { repository.getAll().isEmpty() },
        importing = { imports.get().tasks.first().any { !it.finished } },
    )
}

/**
 * The bundled dictionaries are installed on first launch; until the first one is, the registry is empty. An empty
 * registry without a running import means the user removed everything.
 */
internal suspend fun noTermDictionary(
    hasTermDictionaries: Boolean,
    registryEmpty: suspend () -> Boolean,
    importing: suspend () -> Boolean,
): NoTermDictionary? = when {
    hasTermDictionaries -> null
    registryEmpty() && importing() -> NoTermDictionary.INSTALLING
    else -> NoTermDictionary.NONE_ON
}

/** See [DictionaryLookup.noTermDictionary]. */
enum class NoTermDictionary {
    /** Nothing is installed yet while an import runs, as the bundled dictionaries on first launch. */
    INSTALLING,

    /** Every dictionary with definitions for the language is off, deleted, or without its files. */
    NONE_ON,
}

/**
 * The script a word of a language written in several is shown in: the chosen one when every word is, else the word's
 * own, the script of the [context] around it, or the chosen one for words written alike; null for a language with one.
 */
internal fun scriptTag(word: String, context: String?, settings: LookupSettings, support: LanguageSupport): String? {
    if (support.scriptTags.isEmpty()) return null
    if (settings.fixedScript) return settings.script
    return support.scriptOf(word) ?: context ?: settings.script
}

/** A word to look up for a typed reading: its [spelling] in the chosen script, else the [original] listed word. */
internal data class ReadingWord(val spelling: String, val original: String)

/**
 * The words of each dictionary's reading index list ([lists], highest priority first) in turns, so the likely common
 * ones of each come first, each spelled in [script] where it is written in another, once.
 */
internal fun readingWords(lists: List<List<String>>, script: String?, support: LanguageSupport): List<ReadingWord> {
    val words = LinkedHashMap<String, ReadingWord>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (index in 0 until longest) {
        for (list in lists) {
            val word = list.getOrNull(index) ?: continue
            val written = support.scriptOf(word)
            val spelling = if (script != null && written != null && written != script) {
                support.inScript(MappedText.identity(word), script).firstOrNull()?.text ?: word
            } else {
                word
            }
            words.putIfAbsent(spelling, ReadingWord(spelling, word))
        }
    }
    return words.values.toList()
}

/**
 * Words looked up for one typed reading at most. A syllable lists a few hundred characters, the likely common ones
 * first ([ReadingIndex]), and each takes a lookup.
 */
internal const val MAX_READING_WORDS = 80

private const val TAG = "DictionaryLookup"

/**
 * Results the engine returns for [limit] shown ones. The engine cuts its list without the dictionary order, so with
 * several dictionaries with definitions it returns every candidate and the cut comes after [YomitanSorter]. A lookup
 * rarely finds more than a few dozen candidates, and reading them all costs about as much as reading [limit].
 */
internal fun engineLimit(limit: Int, termDictionaries: Int): Int = if (termDictionaries > 1) Int.MAX_VALUE else limit

/** Scan length of [DictionaryLookup.lookupQuery]: the whole query in characters, within the setting's range. */
internal fun queryScanLength(text: String): Int =
    text.codePointCount(0, text.length).coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH)

/** The popup shows the first frequency of an entry: the one the results are sorted by. */
internal fun LookupResult.withSortFrequencyFirst(dictionary: String?): LookupResult {
    val frequencies = term.frequencies
    if (dictionary == null || frequencies.size < 2 || frequencies.first().dictionary == dictionary) return this
    return copy(term = term.copy(frequencies = frequencies.sortedBy { it.dictionary != dictionary }))
}

/**
 * A result found in a variant of [source] (replaced or converted text): the matched part becomes the source
 * characters it came from, and the conversion counts as one more normalization step for sorting.
 */
internal fun LookupResult.inSource(source: String, variant: MappedText): LookupResult {
    if (variant.text == source) return this
    val length = variant.sourceLength(matched.length.coerceAtMost(variant.text.length))
    return copy(matched = source.substring(0, length), preprocessorSteps = preprocessorSteps + 1)
}

/**
 * The results of [source] spelled as the language keys its headwords (`LanguageSupport.keyedSpelling`) unless its
 * first word is found exactly as written: those that match as much as any found as written ([written]) or more, at a
 * length no word matches exactly as written, keep the letters [source] writes (`LanguageSupport.keepsWrittenLetters`)
 * and were not found already. Kazakh Қазакстан, with қ read as к, finds Қазақстан beside қаз, the only word found as
 * written; Kyrgyz Түштук, found as түшүү through an ending, finds түштүк as well, after it, being one more step from
 * the text; "оң", a word as written, shows only оң.
 *
 * @param lookup the engine's results for a text.
 */
internal suspend fun keyedMatches(
    source: String,
    written: List<LookupResult>,
    support: LanguageSupport,
    lookup: suspend (String) -> List<LookupResult>,
): List<LookupResult> {
    val longest = written.maxOfOrNull { it.matched.length } ?: 0
    val exact = written.filter { it.trace.isEmpty() }.mapTo(HashSet()) { it.matched.length }
    val spelling = support.keyedSpelling(source, exact.maxOrNull() ?: 0) ?: return emptyList()
    val found = written.mapTo(HashSet()) { it.term.expression to it.term.reading }
    return lookup(spelling).fromVariant(source, MappedText.identity(spelling), support)
        .mapNotNull { it.shownBy(support) }
        .filter { it.matched.length >= longest && it.matched.length !in exact }
        .filter { (it.term.expression to it.term.reading) !in found && support.keepsWrittenLetters(source, it.term.expression) }
}

/**
 * What the first of [support]'s misread spellings of [source] finds ([LanguageSupport.misreadSpellings]: Macedonian
 * лубов read for љубов), as [shown] keeps it and mapped back to [source]; for a word from OCR that finds nothing as
 * read. Empty when no spelling finds anything.
 */
internal suspend fun misreadMatches(
    source: String,
    support: LanguageSupport,
    lookup: suspend (String) -> List<LookupResult>,
    shown: suspend (List<LookupResult>) -> List<LookupResult>,
): List<LookupResult> {
    for (spelling in support.misreadSpellings(source)) {
        val found = shown(lookup(spelling.text).fromVariant(source, spelling, support).mapNotNull { it.shownBy(support) })
        if (found.isNotEmpty()) return found
    }
    return emptyList()
}

/**
 * The candidates of a lookup over a language's dictionaries and its fallback language's ([fallback], their titles):
 * those with definitions in the language's own dictionaries, without the fallback's definitions, when any has them;
 * else every candidate, each marked with the fallback language's [code]. Slovak finds voda in its own dictionaries
 * only, and v, which they lack, in the Czech ones.
 *
 * @param written the results found as written, from every loaded dictionary.
 * @param withKeys [written]'s candidates: the results given with those found through keys, whose engine results the
 *   second argument keeps to the dictionaries in use.
 */
internal suspend fun preferringOwn(
    written: List<LookupResult>,
    fallback: Set<String>,
    code: String?,
    withKeys: suspend (found: List<LookupResult>, kept: (List<LookupResult>) -> List<LookupResult>) -> List<LookupResult>,
): List<LookupResult> {
    val own = withKeys(written.withoutDefinitionsOf(fallback)) { it.withoutDefinitionsOf(fallback) }
    if (own.isNotEmpty() || fallback.isEmpty() || code == null) return own
    return withKeys(written) { it }.map { it.copy(fallbackLanguage = code) }
}

/** The results with definitions in other dictionaries than [dictionaries], without those of [dictionaries]. */
internal fun List<LookupResult>.withoutDefinitionsOf(dictionaries: Set<String>): List<LookupResult> {
    if (dictionaries.isEmpty()) return this
    return mapNotNull { result ->
        val kept = result.term.glossaries.filter { it.dictionary !in dictionaries }
        when {
            kept.isEmpty() -> null
            kept.size == result.term.glossaries.size -> result
            else -> result.copy(term = result.term.copy(glossaries = kept))
        }
    }
}
