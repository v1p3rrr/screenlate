package com.vpr.screenlate.languages

import android.util.Log
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The files a language brings: the dictionaries whose source language it is, and the recognition models of its catalog
 * entries that no other turned-on language uses. Dictionaries without a language serve every language and never
 * count.
 */
@Singleton
class LanguageFiles @Inject constructor(
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
    private val store: OcrModelStore,
    private val catalog: DictionaryCatalog,
    private val profiles: LanguageProfiles,
) {
    suspend fun dictionaries(code: String): List<DictionaryEntity> = repository.getAll().filter { it.sourceLanguage == code }

    /** The installed models only [code] uses among the turned-on languages. */
    suspend fun models(code: String): List<InstalledOcrModel> {
        if (store.models.value.isEmpty()) return emptyList()
        val others = profiles.state.first().turnedOn.map { it.code } - code
        val ids = modelsOnlyFor(code, others, withContext(Dispatchers.IO) { catalog.localCatalog() }.entries)
        return store.models.value.filter { it.id in ids }
    }

    /** Whether the language has no files. */
    suspend fun isEmpty(code: String): Boolean = dictionaries(code).isEmpty() && models(code).isEmpty()

    /** The bytes the language's files take on disk. */
    suspend fun size(code: String): Long =
        repository.sizes(dictionaries(code)).values.sum() + withContext(Dispatchers.IO) { models(code).sumOf { it.bytes } }

    /** Deletes the language's files; a bundled dictionary is remembered as deleted, as a single delete does. */
    suspend fun delete(code: String) {
        val dictionaries = dictionaries(code)
        dictionaries.forEach { dictionary ->
            if (dictionary.bundled) bundled.markDeleted(dictionary.title)
            repository.delete(dictionary.id)
        }
        val models = models(code)
        store.delete(models.map { it.id })
        Log.i(TAG, "Deleted the files of $code: ${dictionaries.size} dictionaries, ${models.size} models")
    }

    private companion object {
        const val TAG = "LanguageFiles"
    }
}
