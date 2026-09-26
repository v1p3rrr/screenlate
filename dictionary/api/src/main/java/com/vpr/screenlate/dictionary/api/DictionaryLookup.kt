package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Entry point for lookups: loads the enabled dictionaries on first use and applies the sort settings. */
@Singleton
class DictionaryLookup @Inject constructor(
    private val repository: DictionaryRepository,
    private val engine: DictionaryEngine,
) {
    /** @param primaryReading terms with this reading come first, as for Yomitan's `primary_reading` links. */
    suspend fun lookup(
        text: String,
        language: Language,
        scanLength: Int = DEFAULT_SCAN_LENGTH,
        primaryReading: String? = null,
    ): List<LookupResult> {
        if (text.isBlank()) return emptyList()
        val prepared = repository.prepareLookup(language)
        val options = prepared.options.copy(scanLength = scanLength, primaryReading = primaryReading)
        return YomitanSorter.sort(engine.lookup(text, options), options, prepared.termDictionaries)
    }

    suspend fun styles(language: Language): List<DictionaryStyle> {
        repository.prepareLookup(language)
        return engine.styles()
    }

    suspend fun media(dictionary: String, path: String): ByteArray? = engine.media(dictionary, path)

    /** Whether any enabled dictionary with definitions is installed. */
    suspend fun hasTermDictionaries(): Boolean = repository.getAll().any { it.enabled && it.termCount > 0 }

    companion object {
        /** Characters after the aim point that a lookup considers, as Yomitan's default `scanLength`. */
        const val DEFAULT_SCAN_LENGTH = 16
    }
}
