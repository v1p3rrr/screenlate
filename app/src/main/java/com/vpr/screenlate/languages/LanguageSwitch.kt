package com.vpr.screenlate.languages

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.isFor
import com.vpr.screenlate.dictionary.api.registry.isReadBy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Mandatory categories of [language] that none of [dictionaries] covers: the main dictionary is an enabled one with
 * definitions, any other category an enabled copy of one of its catalog entries; word forms also the whole dictionary
 * they are cut from ([CatalogEntry.isCutFrom]).
 */
internal fun missingCategories(language: Language, dictionaries: List<DictionaryEntity>, catalog: Catalog): Set<CatalogCategory> {
    val own = dictionaries.filter { it.enabled && it.isFor(language) }
    return catalog.mandatory(language).filterTo(linkedSetOf()) { category ->
        when (category) {
            CatalogCategory.MAIN -> own.none { it.termCount > 0 }
            else -> own.none { dictionary ->
                catalog.entries.any { it.category == category && (it.matches(dictionary) || it.isCutFrom(dictionary)) }
            }
        }
    }
}

/** Turns languages on and off with the files they need. */
@Singleton
class LanguageSwitch @Inject constructor(
    private val profiles: LanguageProfiles,
    private val files: LanguageFiles,
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
    private val imports: DictionaryImports,
    private val catalog: DictionaryCatalog,
) {
    /** The language the first run preselects: Japanese, whose dictionaries earlier versions shipped with the app. */
    val bundledLanguage: Language get() = bundled.language

    /**
     * Whether turning [language] on goes through its download screen: its own mandatory dictionaries are not installed.
     * The bundled dictionaries' language needs none while the app carries their archives.
     */
    suspend fun needsDownloads(language: Language): Boolean {
        if (language == bundled.language && bundled.all().isNotEmpty()) return false
        val own = repository.getAll().filter { it.sourceLanguage == language.dictionaryCode }
        val catalog = withContext(Dispatchers.IO) { catalog.localCatalog() }
        return missingCategories(language, own, catalog).isNotEmpty()
    }

    /**
     * Turns [language] on and makes it active (the only language when it answers the [firstRun]) and queues
     * [downloads]. [enableDictionaries] are selected installed copies, enabled without downloading again.
     * The bundled dictionaries' language gets them installed, all of them again when none of its
     * dictionaries is left.
     */
    suspend fun turnOn(
        language: Language,
        downloads: List<CatalogEntry> = emptyList(),
        firstRun: Boolean = false,
        enableDictionaries: Set<Long> = emptySet(),
    ) {
        withContext(NonCancellable) {
            repository.getAll().filter { !it.enabled && it.id in enableDictionaries && it.isReadBy(language) }
                .forEach { repository.setEnabled(it.id, true) }
            if (firstRun) profiles.finishFirstRun(language) else profiles.turnOn(language)
            if (language == bundled.language) {
                if (files.dictionaries(language).isEmpty()) bundled.reinstallAll()
                imports.installBundled()
            }
            downloads.forEach { entry ->
                if (entry.isModel) {
                    imports.downloadModel(entry)
                } else {
                    imports.download(entry.downloadUrl, entry.title, indexUrl = entry.indexUrl.takeIf { entry.resolveLatest }, entry = entry)
                }
            }
            val bytes = downloads.filterNot { it.isBundled }.sumOf { it.downloadBytes }
            val models = downloads.count { it.isModel }
            Log.i(TAG, "Turned on ${language.code}${if (firstRun) " (first run)" else ""}: ${downloads.size} downloads " +
                "($models models, ${bytes shr 20} MB)")
        }
    }

    /** The bytes [language]'s files take; zero for downloads only, null when it has neither files nor downloads. */
    suspend fun filesSize(language: Language): Long? =
        if (files.isEmpty(language)) null else files.size(language)

    /**
     * Turns [language] off; its settings stay. With [deleteFiles] its dictionaries, models and unfinished downloads go
     * too, except the dictionaries another turned-on language reads. Returns false for the last language, which stays
     * on.
     */
    suspend fun turnOff(language: Language, deleteFiles: Boolean): Boolean = withContext(NonCancellable) {
        if (!profiles.turnOff(language)) return@withContext false
        if (deleteFiles) {
            files.delete(language)
            Log.i(TAG, "Turned off ${language.code}: files deleted")
        } else {
            Log.i(TAG, "Turned off ${language.code}: files kept")
        }
        true
    }

    private companion object {
        const val TAG = "LanguageSwitch"
    }
}
