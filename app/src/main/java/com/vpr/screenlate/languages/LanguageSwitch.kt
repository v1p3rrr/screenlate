package com.vpr.screenlate.languages

import android.util.Log
import com.vpr.screenlate.core.common.Language
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Mandatory categories of [language] that none of [dictionaries] covers: the main dictionary is an enabled one with
 * definitions, any other category an enabled copy of one of its catalog entries.
 */
internal fun missingCategories(language: Language, dictionaries: List<DictionaryEntity>, catalog: Catalog): Set<CatalogCategory> {
    val own = dictionaries.filter { it.enabled && it.isFor(language) }
    return catalog.mandatory(language.code).filterTo(linkedSetOf()) { category ->
        when (category) {
            CatalogCategory.MAIN -> own.none { it.termCount > 0 }
            else -> own.none { dictionary -> catalog.entries.any { it.category == category && it.matches(dictionary) } }
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
    /** The language whose dictionaries ship with the app; it needs no downloads, so the first run preselects it. */
    val bundledLanguage: Language get() = bundled.language

    /**
     * Whether turning [language] on goes through its download screen: its own mandatory dictionaries are not installed.
     * The bundled dictionaries' language needs none.
     */
    suspend fun needsDownloads(language: Language): Boolean {
        if (language == bundled.language) return false
        val own = repository.getAll().filter { it.sourceLanguage == language.code }
        val catalog = withContext(Dispatchers.IO) { catalog.localCatalog() }
        return missingCategories(language, own, catalog).isNotEmpty()
    }

    /**
     * Turns [language] on and makes it active (the only language when it answers the [firstRun]) and queues
     * [downloads]. The bundled dictionaries' language gets them installed, all of them again when none of its
     * dictionaries is left.
     */
    suspend fun turnOn(language: Language, downloads: List<CatalogEntry> = emptyList(), firstRun: Boolean = false) {
        withContext(NonCancellable) {
            if (firstRun) profiles.finishFirstRun(language) else profiles.turnOn(language)
            if (language == bundled.language) {
                if (files.dictionaries(language.code).isEmpty()) bundled.reinstallAll()
                imports.installBundled()
            }
            downloads.forEach { entry ->
                if (entry.isModel) {
                    imports.downloadModel(entry)
                } else {
                    imports.download(entry.downloadUrl, entry.title, indexUrl = entry.indexUrl.takeIf { entry.resolveLatest })
                }
            }
            val bytes = downloads.sumOf { it.downloadBytes }
            val models = downloads.count { it.isModel }
            Log.i(TAG, "Turned on ${language.code}${if (firstRun) " (first run)" else ""}: ${downloads.size} downloads " +
                "($models models, ${bytes shr 20} MB)")
        }
    }

    /** The bytes [language]'s files take; null when it has none. */
    suspend fun filesSize(language: Language): Long? =
        if (files.isEmpty(language.code)) null else files.size(language.code)

    /**
     * Turns [language] off; its settings stay. With [deleteFiles] its dictionaries and models go too, and its downloads
     * still queued or running are cancelled. Returns false for the last language, which stays on.
     */
    suspend fun turnOff(language: Language, deleteFiles: Boolean): Boolean = withContext(NonCancellable) {
        if (!profiles.turnOff(language)) return@withContext false
        if (deleteFiles) {
            val titles = withContext(Dispatchers.IO) { catalog.localCatalog() }.entries
                .filter { it.sourceLanguage == language.code }
                .mapTo(hashSetOf()) { it.title }
            val unfinished = imports.tasks.first().filter { !it.finished && it.name in titles }
            unfinished.forEach { imports.cancel(it.id) }
            files.delete(language.code)
            Log.i(TAG, "Turned off ${language.code}: files deleted, ${unfinished.size} downloads cancelled")
        } else {
            Log.i(TAG, "Turned off ${language.code}: files kept")
        }
        true
    }

    private companion object {
        const val TAG = "LanguageSwitch"
    }
}
