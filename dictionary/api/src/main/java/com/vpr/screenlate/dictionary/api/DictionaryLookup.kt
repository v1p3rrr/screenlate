package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LookupStart
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for lookups: loads the enabled dictionaries on first use and applies the language's spelling variants,
 * the lookup settings (romaji, result limit, single kanji entries) and the sort settings.
 */
@Singleton
class DictionaryLookup @Inject constructor(
    private val repository: DictionaryRepository,
    private val engine: DictionaryEngine,
    private val lookupSettings: LookupSettingsRepository,
) {
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
        val settings = lookupSettings.current()
        val support = language.support
        val start = support.lookupStart(text, latinAsNative = settings.romaji) ?: return emptyList()
        val prepared = repository.prepareLookup(language)
        val limit = settings.maxResults.takeIf { it > 0 } ?: Int.MAX_VALUE
        val options = prepared.options.copy(
            scanLength = scanLength ?: settings.scanLength,
            primaryReading = primaryReading,
            maxResults = limit,
        )
        val found = LookupVariants.of(text, settings, support).flatMap { variant ->
            engine.lookup(variant.text, options).map { it.inSource(text, variant) }
        }
        val results = YomitanSorter.sort(found, options, prepared.termDictionaries)
            .filter { start !is LookupStart.Whole || it.matched.length == start.length }
            .distinctBy { it.term.expression to it.term.reading }
            .take(limit)
        if (!extraEntries || !settings.singleKanji) return results
        return results + singleCharacterEntries(results, language, options, prepared.termDictionaries)
    }

    /**
     * Entries for the characters of the longest match that the language gives entries of their own (kanji), as
     * words of a term dictionary; terms already shown are skipped.
     */
    private suspend fun singleCharacterEntries(
        results: List<LookupResult>,
        language: Language,
        options: LookupOptions,
        dictionaryOrder: List<String>,
    ): List<LookupResult> {
        val longest = results.maxByOrNull { it.matched.length }?.matched ?: return emptyList()
        val shown = results.mapTo(mutableSetOf()) { it.term.expression to it.term.reading }
        return language.support.singleCharacterEntries(longest).flatMap { character ->
            val entries = engine.lookup(character, options.copy(scanLength = 1, primaryReading = null))
                .filter { it.term.expression == character && it.deinflected == character }
                .filter { shown.add(it.term.expression to it.term.reading) }
            YomitanSorter.sort(entries, options, dictionaryOrder)
        }
    }

    /** The lookup settings in effect. */
    suspend fun settings(): LookupSettings = lookupSettings.current()

    val settingsUpdates: Flow<LookupSettings> get() = lookupSettings.settings

    suspend fun styles(language: Language): List<DictionaryStyle> {
        repository.prepareLookup(language)
        return engine.styles()
    }

    suspend fun media(dictionary: String, path: String): ByteArray? = engine.media(dictionary, path)

    /** Entries for one character from the enabled kanji dictionaries; empty when there are none. */
    suspend fun kanji(character: String, language: Language): KanjiResult {
        repository.prepareLookup(language)
        return engine.kanji(character)
    }

    /** `rank-based` or `occurrence-based` for each enabled frequency dictionary that declares it, by title. */
    suspend fun frequencyModes(): Map<String, String> = repository.getAll()
        .filter { it.enabled }
        .mapNotNull { entity -> entity.frequencyMode?.let { entity.title to it } }
        .toMap()

    /** Whether any enabled dictionary with definitions is installed. */
    suspend fun hasTermDictionaries(): Boolean = repository.getAll().any { it.enabled && it.termCount > 0 }
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
