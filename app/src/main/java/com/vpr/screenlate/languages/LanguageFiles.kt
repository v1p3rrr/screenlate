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
import java.text.Collator
import java.util.Locale
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
    return tasks.filter {
        !it.finished && if (it.language != null) it.language == code
        else it.name in names || (code == bundledLanguage.code && it.source == SOURCE_BUNDLED)
    }
}

/** An installed recognition model with the catalog's name for it. */
data class LeftoverModel(val id: String, val title: String, val bytes: Long)

/**
 * The files left by a language that is not turned on: its dictionaries (with [sizes] by id) and the recognition models
 * no turned-on language uses. [language] is null for a code this version does not support.
 */
data class LanguageLeftovers(
    val code: String,
    val dictionaries: List<DictionaryEntity>,
    val models: List<LeftoverModel>,
    val sizes: Map<Long, Long>,
) {
    val language: Language? get() = Language.of(code)

    /** Null until every dictionary's size is known. */
    val bytes: Long?
        get() = dictionaries.map { sizes[it.id] }.takeIf { it.all { size -> size != null } }
            ?.sumOf { it!! }?.plus(models.sumOf { it.bytes })
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

    /**
     * The languages not in [turnedOn] that still have files, ordered by name: supported ones first, then codes this
     * version does not support.
     */
    suspend fun leftovers(turnedOn: List<Language>): List<LanguageLeftovers> {
        val on = turnedOn.map { it.code }
        val dictionaries = repository.getAll().filter { it.sourceLanguage != null && it.sourceLanguage !in on }
        val entries = withContext(Dispatchers.IO) { catalog.localCatalog() }.entries
        val installed = store.models.value
        val codes = (dictionaries.mapNotNull { it.sourceLanguage } + entries.filter { it.isModel }.map { it.sourceLanguage })
            .filter { it !in on }
            .distinct()
        val sizes = repository.sizes(dictionaries)
        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale)
        return withContext(Dispatchers.IO) {
            codes.mapNotNull { code ->
                val ids = modelsOnlyFor(code, on, entries)
                val models = installed.filter { it.id in ids }.map { model ->
                    LeftoverModel(model.id, entries.firstOrNull { it.model == model.id }?.displayTitle(locale) ?: model.id, model.bytes)
                }
                val own = dictionaries.filter { it.sourceLanguage == code }
                if (own.isEmpty() && models.isEmpty()) null else LanguageLeftovers(code, own, models, sizes)
            }
        }.sortedWith(
            compareBy<LanguageLeftovers> { it.language == null }
                .thenBy(collator) { Locale.forLanguageTag(it.code).getDisplayLanguage(locale) },
        )
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
     * Deletes the language's files and cancels its unfinished downloads first, dismissing its stopped ones; a bundled
     * dictionary is remembered as deleted, as a single delete does.
     */
    suspend fun delete(code: String) {
        val installed = dictionaries(code)
        val catalog = withContext(Dispatchers.IO) { catalog.localCatalog() }
        val tasks = imports.tasks.first()
        val downloads = downloadsOf(code, tasks, catalog, installed, bundled.language)
        downloads.forEach { imports.cancelAndAwait(it.id) }
        // Stopped downloads keep what they got for a resume.
        tasks.filter { it.resumable && it.language == code }.forEach { imports.dismiss(it.id) }
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
