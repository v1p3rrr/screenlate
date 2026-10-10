package com.vpr.screenlate.languages

import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Unfinished [tasks] that would bring the files of the language with [code] back once they are deleted: downloads of
 * its catalog entries, updates of its [installed] dictionaries, and the bundled install for [bundledLanguage].
 */
internal fun downloadsOf(
    code: String,
    tasks: List<ImportTask>,
    catalog: Catalog,
    installed: List<DictionaryEntity>,
    bundledLanguage: Language,
): List<ImportTask> {
    val names = catalog.entries.filter { it.sourceLanguage == code }.mapTo(hashSetOf()) { it.title }
    installed.mapTo(names) { it.title }
    return tasks.filter { !it.finished && (it.name in names || (code == bundledLanguage.code && it.source == SOURCE_BUNDLED)) }
}

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
    private val imports: DictionaryImports,
) {
    suspend fun dictionaries(code: String): List<DictionaryEntity> = repository.getAll().filter { it.sourceLanguage == code }

    /** The installed models only [code] uses among the turned-on languages. */
    suspend fun models(code: String): List<InstalledOcrModel> {
        if (store.models.value.isEmpty()) return emptyList()
        val others = profiles.state.first().turnedOn.map { it.code } - code
        val ids = modelsOnlyFor(code, others, withContext(Dispatchers.IO) { catalog.localCatalog() }.entries)
        return store.models.value.filter { it.id in ids }
    }

    /** Whether the language has no files or unfinished downloads that could install them. */
    suspend fun isEmpty(code: String): Boolean {
        val installed = dictionaries(code)
        if (installed.isNotEmpty() || models(code).isNotEmpty()) return false
        return downloadsOf(code, imports.tasks.first(), withContext(Dispatchers.IO) { catalog.localCatalog() }, installed, bundled.language).isEmpty()
    }

    /** The bytes the language's files take on disk. */
    suspend fun size(code: String): Long =
        repository.sizes(dictionaries(code)).values.sum() + withContext(Dispatchers.IO) { models(code).sumOf { it.bytes } }

    /**
     * Deletes the language's files and cancels its unfinished downloads first; a bundled dictionary is remembered as
     * deleted, as a single delete does.
     */
    suspend fun delete(code: String) {
        val installed = dictionaries(code)
        val catalog = withContext(Dispatchers.IO) { catalog.localCatalog() }
        val downloads = downloadsOf(code, imports.tasks.first(), catalog, installed, bundled.language)
        downloads.forEach { imports.cancelAndAwait(it.id) }
        // An import may have registered a dictionary just before cancellation; include it in the deletion.
        val dictionaries = dictionaries(code)
        dictionaries.forEach { dictionary ->
            if (dictionary.bundled) bundled.markDeleted(dictionary.title)
            repository.delete(dictionary.id)
        }
        val models = models(code)
        store.delete(models.map { it.id })
        Log.i(
            TAG,
            "Deleted the files of $code: ${dictionaries.size} dictionaries, ${models.size} models, " +
                "${downloads.size} downloads cancelled",
        )
    }

    private companion object {
        const val TAG = "LanguageFiles"
    }
}
