package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.LookupStart
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
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

    /**
     * @param scanLength characters of [text] to consider; the scan length setting when null.
     * @param primaryReading terms with this reading come first, as for Yomitan's `primary_reading` links.
     * @param extraEntries add the single kanji entries below the results when that setting is on.
     */
    suspend fun lookup(
        text: String,
        language: Language,
        scanLength: Int? = null,
        primaryReading: String? = null,
        extraEntries: Boolean = true,
    ): List<LookupResult> {
        if (text.isBlank()) return emptyList()
        val settings = lookupSettings.current(language)
        val support = language.support
        val start = support.lookupStart(text, latinAsNative = settings.romaji) ?: return emptyList()
        return repository.withLookup(language) { prepared ->
            val limit = settings.maxResults.takeIf { it > 0 } ?: Int.MAX_VALUE
            val options = prepared.options.copy(
                scanLength = scanLength ?: settings.scanLength,
                primaryReading = primaryReading,
                maxResults = limit,
                language = language,
            )
            val engineOptions = options.copy(maxResults = engineLimit(limit, prepared.termDictionaries.size))
            val found = LookupVariants.of(text, settings, support).flatMap { variant ->
                engine.lookup(variant.text, engineOptions).map { it.asShown(support).inSource(text, variant) }
            }
            val script = settings.script?.takeIf { settings.fixedScript }
            val results = YomitanSorter.sort(found, options, prepared.termDictionaries)
                .filter { start !is LookupStart.Whole || it.matched.length == start.length }
                .mergeSameWords(prepared.termDictionaries)
                .let { if (script != null) it.inScript(script, support, prepared.termDictionaries) else it }
                .take(limit)
                .map { it.withSortFrequencyFirst(options.frequencyDictionary) }
                .withTranscriptions(support)
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
     * covers the query, up to [LookupSettings.MAX_SCAN_LENGTH] characters, instead of the scan length setting.
     */
    suspend fun lookupQuery(text: String, language: Language, primaryReading: String? = null): List<LookupResult> =
        lookup(text, language, scanLength = queryScanLength(text), primaryReading = primaryReading)

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
        return map { Transcriptions.prepare(it, tags, support.transcriptionRegionNames) }
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
