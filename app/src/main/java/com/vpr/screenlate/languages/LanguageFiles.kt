package com.vpr.screenlate.languages

import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files a language brings: the dictionaries whose source language it is. Dictionaries without a language serve
 * every language and never count.
 */
@Singleton
class LanguageFiles @Inject constructor(
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
) {
    suspend fun dictionaries(code: String): List<DictionaryEntity> = repository.getAll().filter { it.sourceLanguage == code }

    /** The bytes the language's files take on disk. */
    suspend fun size(code: String): Long = repository.sizes(dictionaries(code)).values.sum()

    /** Deletes the language's dictionaries; a bundled one is remembered as deleted, as a single delete does. */
    suspend fun delete(code: String) {
        dictionaries(code).forEach { dictionary ->
            if (dictionary.bundled) bundled.markDeleted(dictionary.title)
            repository.delete(dictionary.id)
        }
    }
}
