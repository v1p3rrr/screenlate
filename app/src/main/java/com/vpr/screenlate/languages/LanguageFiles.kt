package com.vpr.screenlate.languages

import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.languageName
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
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
 * The codes of the files that go with [code] while the languages [turnedOn] stay on: a language's own (its models),
 * and its dictionaries' unless another turned-on language reads them too. A dictionary code stands for those of its
 * languages that are off ("sh" for Croatian and Serbian), a code this version does not support for itself.
 */
internal fun fileCodes(code: String, turnedOn: Collection<Language>): Set<String> {
    val entries = Language.of(code)?.let(::listOf) ?: Language.withDictionaries(code)
    if (entries.isEmpty()) return setOf(code)
    val read = turnedOn.mapTo(hashSetOf()) { it.dictionaryCode }
    return (entries - turnedOn.toSet()).flatMapTo(linkedSetOf()) { entry ->
        listOfNotNull(entry.code, entry.dictionaryCode.takeIf { it !in read })
    }
}

/**
 * The leftover card the models of the language with [code] go under: its dictionaries' while no language reading them
 * is on, so that languages sharing dictionaries have one card; else its own.
 */
internal fun leftoverCode(code: String, turnedOn: Collection<Language>): String {
    val language = Language.of(code) ?: return code
    return language.dictionaryCode.takeIf { shared -> turnedOn.none { it.dictionaryCode == shared } } ?: code
}

/**
 * Unfinished [tasks] that would bring the files with [codes] ([fileCodes]) back once they are deleted: downloads of
 * their catalog entries, updates of the [installed] dictionaries, and the bundled install for [bundledLanguage].
 */
internal fun downloadsOf(
    codes: Set<String>,
    tasks: List<ImportTask>,
    catalog: Catalog,
    installed: List<DictionaryEntity>,
    bundledLanguage: Language,
): List<ImportTask> {
    val names = catalog.entries.filter { it.sourceLanguage in codes }.mapTo(hashSetOf()) { it.title }
    installed.mapTo(names) { it.title }
    return tasks.filter {
        !it.finished && if (it.language != null) it.language in codes
        else it.name in names || (bundledLanguage.code in codes && it.source == SOURCE_BUNDLED)
    }
}

/** An installed recognition model with the catalog's name for it. */
data class LeftoverModel(val id: String, val title: String, val bytes: Long)

/**
 * The files left by a language that is not turned on: its dictionaries (with [sizes] by id) and the recognition models
 * no turned-on language uses. [code] is a language's, or the dictionaries' code of languages that share them and are
 * all off.
 */
data class LanguageLeftovers(
    val code: String,
    val dictionaries: List<DictionaryEntity>,
    val models: List<LeftoverModel>,
    val sizes: Map<Long, Long>,
) {
    /** The languages that can be turned on with these files: Croatian and Serbian for "sh", none for an unsupported code. */
    val languages: List<Language> get() = Language.of(code)?.let(::listOf) ?: Language.withDictionaries(code)

    /** Null until every dictionary's size is known. */
    val bytes: Long?
        get() = dictionaries.map { sizes[it.id] }.takeIf { it.all { size -> size != null } }
            ?.sumOf { it!! }?.plus(models.sumOf { it.bytes })
}

/**
 * By language code, the [installed] models of the languages not in [turnedOn] that no turned-on language uses; a model
 * several such languages list is under each of them. Reads the models' sizes from disk.
 */
internal fun leftoverModels(
    turnedOn: Collection<String>,
    entries: List<CatalogEntry>,
    installed: List<InstalledOcrModel>,
    locale: Locale,
): Map<String, List<LeftoverModel>> {
    if (installed.isEmpty()) return emptyMap()
    return entries.filter { it.isModel && it.sourceLanguage !in turnedOn }.map { it.sourceLanguage }.distinct()
        .associateWith { code ->
            val ids = modelsOnlyFor(code, turnedOn, entries)
            installed.filter { it.id in ids }.map { model ->
                LeftoverModel(model.id, entries.firstOrNull { it.model == model.id }?.displayTitle(locale) ?: model.id, model.bytes)
            }
        }
        .filterValues { it.isNotEmpty() }
}

/**
 * [leftoverModels] by leftover card ([leftoverCode]): the models of Croatian and Serbian go under sh while both are
 * off.
 */
internal fun leftoverCards(
    turnedOn: List<Language>,
    entries: List<CatalogEntry>,
    installed: List<InstalledOcrModel>,
    locale: Locale,
): Map<String, List<LeftoverModel>> = leftoverModels(turnedOn.map { it.code }, entries, installed, locale).entries
    .groupBy({ leftoverCode(it.key, turnedOn) }, { it.value })
    .mapValues { (_, lists) -> lists.flatten().distinctBy { it.id } }

/**
 * The files a language brings: the dictionaries whose source language it is, unless another language that reads them
 * is on, and the recognition models of its catalog entries that no other turned-on language uses.
 * Dictionaries without a language serve every language and never count.
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
    /** The dictionaries [language] reads, also those another entry of its dictionaries shares. */
    suspend fun dictionaries(language: Language): List<DictionaryEntity> =
        repository.getAll().filter { it.sourceLanguage == language.dictionaryCode }

    private suspend fun dictionaries(codes: Set<String>): List<DictionaryEntity> = repository.getAll().filter { it.sourceLanguage in codes }

    /** The installed models only [codes] use among the turned-on languages. */
    private suspend fun models(codes: Set<String>): List<InstalledOcrModel> {
        if (store.models.value.isEmpty()) return emptyList()
        val others = profiles.state.first().turnedOn.map { it.code } - codes
        val entries = withContext(Dispatchers.IO) { catalog.localCatalog() }.entries
        val ids = codes.flatMapTo(hashSetOf()) { modelsOnlyFor(it, others, entries) }
        return store.models.value.filter { it.id in ids }
    }

    /** The codes of [language]'s files ([fileCodes]), as if it were off. */
    private suspend fun codesOf(language: Language): Set<String> = fileCodes(language.code, profiles.state.first().turnedOn - language)

    /**
     * The languages not in [turnedOn] that still have files, ordered by name: supported ones first, then codes this
     * version does not support.
     */
    suspend fun leftovers(turnedOn: List<Language>): List<LanguageLeftovers> {
        val read = turnedOn.mapTo(hashSetOf()) { it.dictionaryCode }
        val dictionaries = repository.getAll().filter { it.sourceLanguage != null && it.sourceLanguage !in read }
        val entries = withContext(Dispatchers.IO) { catalog.localCatalog() }.entries
        val sizes = repository.sizes(dictionaries)
        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale)
        val models = withContext(Dispatchers.IO) { leftoverCards(turnedOn, entries, store.models.value, locale) }
        val codes = (dictionaries.mapNotNull { it.sourceLanguage } + models.keys).distinct()
        return codes.map { code ->
            LanguageLeftovers(code, dictionaries.filter { it.sourceLanguage == code }, models[code].orEmpty(), sizes)
        }.sortedWith(
            compareBy<LanguageLeftovers> { it.languages.isEmpty() }
                .thenBy(collator) { languageName(it.code, locale) },
        )
    }

    /** Whether the language has no files or unfinished downloads that could install them. */
    suspend fun isEmpty(language: Language): Boolean {
        val codes = codesOf(language)
        val installed = dictionaries(codes)
        if (installed.isNotEmpty() || models(codes).isNotEmpty()) return false
        return downloadsOf(codes, imports.tasks.first(), withContext(Dispatchers.IO) { catalog.localCatalog() }, installed, bundled.language).isEmpty()
    }

    /** The bytes the language's files take on disk. */
    suspend fun size(language: Language): Long {
        val codes = codesOf(language)
        return repository.sizes(dictionaries(codes)).values.sum() + withContext(Dispatchers.IO) { models(codes).sumOf { it.bytes } }
    }

    /** Deletes [language]'s files as the leftover cards' delete does. */
    suspend fun delete(language: Language) = delete(codesOf(language))

    /** Deletes the files of a leftover card's [code] ([LanguageLeftovers.code]). */
    suspend fun delete(code: String) = delete(fileCodes(code, profiles.state.first().turnedOn))

    /**
     * Deletes the files with [codes] and cancels their unfinished downloads first, dismissing the stopped ones; a
     * bundled dictionary is remembered as deleted, as a single delete does.
     */
    private suspend fun delete(codes: Set<String>) {
        val installed = dictionaries(codes)
        val catalog = withContext(Dispatchers.IO) { catalog.localCatalog() }
        val tasks = imports.tasks.first()
        val downloads = downloadsOf(codes, tasks, catalog, installed, bundled.language)
        downloads.forEach { imports.cancelAndAwait(it.id) }
        // Stopped downloads keep what they got for a resume.
        tasks.filter { it.resumable && it.language in codes }.forEach { imports.dismiss(it.id) }
        // An import may have registered a dictionary just before cancellation; include it in the deletion.
        val dictionaries = dictionaries(codes)
        dictionaries.forEach { dictionary ->
            if (dictionary.bundled) bundled.markDeleted(dictionary.title)
            repository.delete(dictionary.id)
        }
        val models = models(codes)
        store.delete(models.map { it.id })
        Log.i(
            TAG,
            "Deleted the files of ${codes.joinToString()}: ${dictionaries.size} dictionaries, ${models.size} models, " +
                "${downloads.size} downloads cancelled",
        )
    }

    private companion object {
        const val TAG = "LanguageFiles"
    }
}
