package com.vpr.screenlate.dictionary.api.registry

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.TagBanks
import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import com.vpr.screenlate.dictionary.api.languages.DictionarySample
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of imported dictionaries and the single place that changes them. Every change reloads the engine
 * with the enabled dictionaries in priority order.
 */
@Singleton
class DictionaryRepository @Inject constructor(
    private val dao: DictionaryDao,
    private val engine: DictionaryEngine,
    private val storage: DictionaryStorage,
    private val preferences: DataStore<Preferences>,
    private val languageDetector: DictionaryLanguageDetector,
) {
    private val mutex = Mutex()
    private var loadedLanguage: Language? = null
    private var sortDictionary: DictionaryEntity? = null
    private var termOrder: List<String> = emptyList()
    private val changes = AtomicInteger()

    /**
     * Changes whenever the loaded dictionaries, their switches, order or files may have changed; values derived from
     * them (styles, tag descriptions) can be kept until it does.
     */
    val generation: Int get() = changes.get()

    val dictionaries: Flow<List<DictionaryEntity>> = dao.observeAll()

    /** The frequency dictionary chosen for sorting; null means the first enabled one. */
    val sortDictionaryId: Flow<Long?> = preferences.data.map { it[SORT_DICTIONARY] }

    suspend fun getAll(): List<DictionaryEntity> = dao.getAll()

    /** Reloads the engine, e.g. after missing files were restored. */
    suspend fun reload() = mutex.withLock { reloadLocked() }

    /**
     * Imports a Yomitan archive. The dictionary [replaces] (an update, whose title may differ) or else one with
     * the same title is replaced and keeps its position and enabled state. Languages missing from index.json are
     * taken from the matching [catalog] entry (JMdict names none), then from the replaced dictionary, then from the
     * archive's content ([DictionaryLanguageDetector]), so the dictionary is used for its language only. Frequency
     * and pitch dictionaries keep only a source language.
     */
    suspend fun import(
        archive: File,
        bundled: Boolean = false,
        replaces: Long? = null,
        catalog: List<CatalogEntry> = emptyList(),
    ): DictionaryEntity {
        val staging = storage.newStagingDirectory()
        try {
            val imported = engine.import(archive, staging)
            val tagNotes = runCatching { TagBanks.read(archive) }
                .onFailure { Log.w(TAG, "Reading tag descriptions failed", it) }
                .getOrDefault(emptyMap())
            storage.writeTagNotes(imported.directory, tagNotes)
            val metadata = imported.metadata
            val kind = DictionaryKind.of(metadata)
            val listed = catalog.firstOrNull { it.kind == kind && it.matches(metadata.indexUrl, metadata.title) }
            val known = replaced(replaces, metadata.title, kind)
            val source = metadata.sourceLanguage ?: listed?.sourceLanguage ?: known?.sourceLanguage
            val target = (metadata.targetLanguage ?: listed?.targetLanguage ?: known?.targetLanguage).takeIf { kind.hasTarget }
            val detected = if (source == null || (target == null && kind.hasTarget)) {
                runCatching { languageDetector.detect(DictionarySample.of(archive)) }
                    .onFailure { Log.w(TAG, "Detecting the languages failed", it) }
                    .getOrNull()
            } else {
                null
            }
            return mutex.withLock {
                val existing = replaced(replaces, metadata.title, kind)
                // An update may take the title of another installed dictionary, which it then replaces as well.
                val collided = dao.findByTitle(metadata.title)?.takeIf { it.id != existing?.id }
                collided?.let { dao.delete(it) }
                val entity = DictionaryEntity(
                    id = existing?.id ?: 0,
                    title = metadata.title,
                    revision = metadata.revision,
                    kind = kind,
                    sourceLanguage = source ?: detected?.source,
                    targetLanguage = (target ?: detected?.target).takeIf { kind.hasTarget },
                    frequencyMode = metadata.frequencyMode,
                    enabled = existing?.enabled ?: true,
                    priority = existing?.priority ?: (dao.maxPriority() + 1),
                    directory = storage.adopt(imported.directory),
                    termCount = metadata.termCount,
                    frequencyCount = metadata.frequencyCount,
                    pitchCount = metadata.pitchCount,
                    kanjiCount = metadata.kanjiCount,
                    mediaCount = metadata.mediaCount,
                    isUpdatable = metadata.isUpdatable,
                    indexUrl = metadata.indexUrl,
                    downloadUrl = metadata.downloadUrl,
                    author = metadata.author,
                    url = metadata.url,
                    description = metadata.description,
                    attribution = metadata.attribution,
                    bundled = bundled || existing?.bundled == true,
                    importedAt = System.currentTimeMillis(),
                )
                val saved = if (existing == null) {
                    entity.copy(id = dao.insert(entity))
                } else {
                    dao.update(entity)
                    entity
                }
                reloadLocked()
                // The old files are unmapped only after the reload.
                listOfNotNull(existing, collided).forEach { storage.directoryOf(it).deleteRecursively() }
                saved
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * The dictionary an import replaces: [replaces], else one with the same title, else the only one of the same
     * [kind] whose title differs just by its revision mark (`Jitendex.org [2026-01-04]` for `[2026-08-11]`), as the
     * collection import lists it as installed.
     */
    private suspend fun replaced(replaces: Long?, title: String, kind: DictionaryKind): DictionaryEntity? =
        replaces?.let { dao.get(it) }
            ?: dao.findByTitle(title)
            ?: dao.getAll().filter { it.kind == kind && dictionaryKey(it.title) == dictionaryKey(title) }.singleOrNull()

    /**
     * Decodes index texts stored raw by earlier versions (a line break as a backslash and `n`), once; see
     * [decodeIndexText].
     */
    suspend fun decodeStoredTexts() = mutex.withLock {
        if (preferences.data.first()[TEXTS_DECODED] == true) return@withLock
        for (dictionary in dao.getAll()) {
            val decoded = dictionary.copy(
                revision = decodeIndexText(dictionary.revision) ?: dictionary.revision,
                author = decodeIndexText(dictionary.author),
                url = decodeIndexText(dictionary.url),
                description = decodeIndexText(dictionary.description),
                attribution = decodeIndexText(dictionary.attribution),
                indexUrl = decodeIndexText(dictionary.indexUrl),
                downloadUrl = decodeIndexText(dictionary.downloadUrl),
            )
            if (decoded != dictionary) dao.update(decoded)
        }
        preferences.edit { it[TEXTS_DECODED] = true }
    }

    /** Sets the languages of a dictionary by hand; null clears one. */
    suspend fun setLanguages(id: Long, source: String?, target: String?) = mutex.withLock {
        val dictionary = dao.get(id) ?: return@withLock
        dao.update(dictionary.copy(sourceLanguage = source, targetLanguage = target.takeIf { dictionary.kind.hasTarget }))
        reloadLocked()
    }

    /** Fills in languages that a dictionary does not have yet, keeping the ones it has. */
    suspend fun fillLanguages(id: Long, source: String?, target: String?) = mutex.withLock {
        val dictionary = dao.get(id) ?: return@withLock
        val filled = dictionary.copy(
            sourceLanguage = dictionary.sourceLanguage ?: source,
            targetLanguage = (dictionary.targetLanguage ?: target).takeIf { dictionary.kind.hasTarget },
        )
        if (filled == dictionary) return@withLock
        dao.update(filled)
        if (filled.sourceLanguage != dictionary.sourceLanguage) reloadLocked()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        val dictionary = dao.get(id) ?: return@withLock
        dao.update(dictionary.copy(enabled = enabled))
        reloadLocked()
    }

    /**
     * Applies a new priority order; [ids] lists every dictionary, highest priority first. [enabled] sets switches in
     * the same step, so the engine reloads once.
     */
    suspend fun reorder(ids: List<Long>, enabled: Map<Long, Boolean> = emptyMap()) = mutex.withLock {
        val byId = dao.getAll().associateBy { it.id }
        dao.update(
            ids.mapNotNull { byId[it] }.mapIndexed { index, dictionary ->
                dictionary.copy(priority = index, enabled = enabled[dictionary.id] ?: dictionary.enabled)
            },
        )
        reloadLocked()
    }

    /**
     * Adds a dictionary from a backup: [files] is a directory in the engine's format (see
     * [DictionaryStorage.newStagingDirectory]) that is moved into storage. One with the same title is replaced and
     * keeps its position and switch; a new one goes last with the switch from [dictionary].
     */
    suspend fun restore(dictionary: DictionaryEntity, files: File): DictionaryEntity = mutex.withLock {
        val existing = dao.findByTitle(dictionary.title)
        val entity = dictionary.copy(
            id = existing?.id ?: 0,
            enabled = existing?.enabled ?: dictionary.enabled,
            priority = existing?.priority ?: (dao.maxPriority() + 1),
            directory = storage.adopt(files),
        )
        val saved = if (existing == null) {
            entity.copy(id = dao.insert(entity))
        } else {
            dao.update(entity)
            entity
        }
        reloadLocked()
        existing?.let { storage.directoryOf(it).deleteRecursively() }
        saved
    }

    /** Saves the order, switches and languages of [dictionaries] (full entries) and the sort dictionary. */
    suspend fun applyStates(dictionaries: List<DictionaryEntity>, sortDictionaryId: Long?) = mutex.withLock {
        dao.update(dictionaries)
        if (sortDictionaryId != null) {
            preferences.edit { it[SORT_DICTIONARY] = sortDictionaryId }
        } else {
            preferences.edit { it.remove(SORT_DICTIONARY) }
        }
        reloadLocked()
    }

    suspend fun delete(id: Long) = mutex.withLock {
        val dictionary = dao.get(id) ?: return@withLock
        dao.delete(dictionary)
        reloadLocked()
        storage.directoryOf(dictionary).deleteRecursively()
    }

    /** Loads the engine for [language] unless it is already loaded; returns what lookups need to know. */
    suspend fun prepareLookup(language: Language): PreparedLookup = mutex.withLock {
        if (loadedLanguage != language) load(language)
        val sort = sortDictionary
        PreparedLookup(
            options = LookupOptions(
                frequencyDictionary = sort?.title,
                frequencyOrder = when {
                    sort == null -> FrequencyOrder.DISABLED
                    sort.frequencyMode == "occurrence-based" -> FrequencyOrder.DESCENDING
                    else -> FrequencyOrder.ASCENDING
                },
            ),
            termDictionaries = termOrder,
        )
    }

    suspend fun setSortDictionary(id: Long) = mutex.withLock {
        preferences.edit { it[SORT_DICTIONARY] = id }
        reloadLocked()
    }

    /** Deletes storage leftovers. Call once at startup. */
    suspend fun cleanUp() = mutex.withLock {
        storage.cleanUp(dao.getAll().map { it.directory })
    }

    private suspend fun reloadLocked() {
        changes.incrementAndGet()
        loadedLanguage?.let { load(it) }
    }

    /** Tag descriptions of the enabled dictionaries that have any, for the popup. */
    suspend fun tagNotes(): List<DictionaryTagNotes> = withContext(Dispatchers.IO) {
        dao.getAll().filter { it.enabled }.mapNotNull { dictionary ->
            storage.tagNotes(dictionary).takeIf { it.isNotEmpty() }?.let { DictionaryTagNotes(dictionary.title, it) }
        }
    }

    /**
     * Saves tag descriptions for bundled dictionaries imported before they were kept, reading them from [source]
     * by title; bundled dictionaries it has nothing for are marked as having none, so this runs once.
     */
    suspend fun fillBundledTagNotes(source: suspend (title: String) -> Map<String, String>?) = withContext(Dispatchers.IO) {
        val missing = dao.getAll().filter { it.bundled && storage.hasFiles(it) && !storage.hasTagNotes(it) }
        for (dictionary in missing) {
            val notes = runCatching { source(dictionary.title) }.getOrNull().orEmpty()
            storage.writeTagNotes(storage.directoryOf(dictionary), notes)
        }
        if (missing.isNotEmpty()) changes.incrementAndGet()
    }

    /** Whether lookups in [language] search a dictionary with definitions; reads the registry without loading the engine. */
    suspend fun hasTermDictionaries(language: Language): Boolean = withContext(Dispatchers.IO) {
        dao.getAll().any { it.termCount > 0 && usable(it, language) }
    }

    /** Dictionaries whose files are gone (e.g. after a data transfer that skipped large files). */
    suspend fun missingFiles(): List<DictionaryEntity> = withContext(Dispatchers.IO) {
        dao.getAll().filter { !storage.hasFiles(it) }
    }

    private suspend fun load(language: Language) {
        val enabled = dao.getAll().filter { usable(it, language) }
        fun List<DictionaryEntity>.directories() = map { storage.directoryOf(it) }
        engine.load(
            DictionarySet(
                terms = enabled.filter { it.termCount > 0 }.directories(),
                frequencies = enabled.filter { it.frequencyCount > 0 }.directories(),
                pitches = enabled.filter { it.pitchCount > 0 }.directories(),
                kanji = enabled.filter { it.kanjiCount > 0 }.directories(),
            ),
        )
        val frequencies = enabled.filter { it.frequencyCount > 0 }
        val preferred = preferences.data.first()[SORT_DICTIONARY]
        sortDictionary = frequencies.firstOrNull { it.id == preferred } ?: frequencies.firstOrNull()
        termOrder = enabled.filter { it.termCount > 0 }.map { it.title }
        loadedLanguage = language
        changes.incrementAndGet()
    }

    /** Whether lookups in [language] load [dictionary]. */
    private fun usable(dictionary: DictionaryEntity, language: Language): Boolean =
        dictionary.enabled && dictionary.isFor(language) && storage.hasFiles(dictionary)
}

/**
 * @property termDictionaries titles of the loaded term dictionaries, highest priority first.
 */
data class PreparedLookup(
    val options: LookupOptions,
    val termDictionaries: List<String>,
)

private val SORT_DICTIONARY = longPreferencesKey("sort_dictionary_id")
private val TEXTS_DECODED = booleanPreferencesKey("index_texts_decoded")
private const val TAG = "DictionaryRepository"
