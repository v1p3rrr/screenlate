package com.vpr.screenlate.dictionary.api.registry

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.LookupOptions
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
) {
    private val mutex = Mutex()
    private var loadedLanguage: Language? = null
    private var sortDictionary: DictionaryEntity? = null
    private var termOrder: List<String> = emptyList()

    val dictionaries: Flow<List<DictionaryEntity>> = dao.observeAll()

    /** The frequency dictionary chosen for sorting; null means the first enabled one. */
    val sortDictionaryId: Flow<Long?> = preferences.data.map { it[SORT_DICTIONARY] }

    suspend fun getAll(): List<DictionaryEntity> = dao.getAll()

    /**
     * Imports a Yomitan archive. The dictionary [replaces] (an update, whose title may differ) or else one with
     * the same title is replaced and keeps its position and enabled state.
     */
    suspend fun import(archive: File, bundled: Boolean = false, replaces: Long? = null): DictionaryEntity {
        val staging = storage.newStagingDirectory()
        try {
            val imported = engine.import(archive, staging)
            return mutex.withLock {
                val metadata = imported.metadata
                val existing = replaces?.let { dao.get(it) } ?: dao.findByTitle(metadata.title)
                // An update must not collide with another dictionary that already has the new title.
                if (replaces != null) {
                    dao.findByTitle(metadata.title)?.takeIf { it.id != replaces }?.let { dao.delete(it) }
                }
                val entity = DictionaryEntity(
                    id = existing?.id ?: 0,
                    title = metadata.title,
                    revision = metadata.revision,
                    kind = DictionaryKind.of(metadata),
                    sourceLanguage = metadata.sourceLanguage,
                    targetLanguage = metadata.targetLanguage,
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
                existing?.let { storage.directoryOf(it).deleteRecursively() }
                saved
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        val dictionary = dao.get(id) ?: return@withLock
        dao.update(dictionary.copy(enabled = enabled))
        reloadLocked()
    }

    /** Applies a new priority order; [ids] lists every dictionary, highest priority first. */
    suspend fun reorder(ids: List<Long>) = mutex.withLock {
        val byId = dao.getAll().associateBy { it.id }
        dao.update(ids.mapNotNull { byId[it] }.mapIndexed { index, dictionary -> dictionary.copy(priority = index) })
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
        loadedLanguage?.let { load(it) }
    }

    private suspend fun load(language: Language) {
        val enabled = dao.getAll().filter { dictionary ->
            dictionary.enabled && (dictionary.sourceLanguage == null || dictionary.sourceLanguage == language.code)
        }
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
    }
}

/**
 * @property termDictionaries titles of the loaded term dictionaries, highest priority first.
 */
data class PreparedLookup(
    val options: LookupOptions,
    val termDictionaries: List<String>,
)

private val SORT_DICTIONARY = longPreferencesKey("sort_dictionary_id")
