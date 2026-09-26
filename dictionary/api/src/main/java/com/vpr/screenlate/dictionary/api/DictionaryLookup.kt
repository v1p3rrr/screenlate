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
    suspend fun lookup(text: String, language: Language, scanLength: Int = DEFAULT_SCAN_LENGTH): List<LookupResult> {
        if (text.isBlank()) return emptyList()
        val options = repository.prepareLookup(language)
        return engine.lookup(text, options.copy(scanLength = scanLength))
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
