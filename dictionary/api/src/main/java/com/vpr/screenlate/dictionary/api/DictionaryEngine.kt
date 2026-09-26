package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import java.io.File

/**
 * Storage and lookup backend for Yomitan dictionaries. Implementations live in `dictionary:engine-*` modules and
 * are bound with Hilt; the rest of the app only sees this interface.
 *
 * Implementations are safe to call from any thread and serialize access to their native state internally.
 */
interface DictionaryEngine {
    /**
     * Converts a Yomitan archive into the engine's format inside a new subdirectory of [outputDir].
     *
     * @throws DictionaryImportException if the archive is not a valid dictionary.
     */
    suspend fun import(archive: File, outputDir: File): ImportedDictionary

    /** Replaces the set of dictionaries used by [lookup]; lists are in priority order. */
    suspend fun load(dictionaries: DictionarySet)

    suspend fun lookup(text: String, options: LookupOptions = LookupOptions()): List<LookupResult>

    /** `styles.css` of the loaded term dictionaries that have one. */
    suspend fun styles(): List<DictionaryStyle>

    /** A media file (image) referenced by a glossary, or null if the dictionary has no such file. */
    suspend fun media(dictionary: String, path: String): ByteArray?

    suspend fun kanji(character: String): KanjiResult
}

/** Engine directories to load, each list in priority order. A directory may appear in several lists. */
data class DictionarySet(
    val terms: List<File> = emptyList(),
    val frequencies: List<File> = emptyList(),
    val pitches: List<File> = emptyList(),
    val kanji: List<File> = emptyList(),
)

/**
 * @property scanLength maximum number of characters of the lookup string to consider.
 * @property frequencyDictionary title of the frequency dictionary used for sorting; null disables it.
 * @property primaryReading terms with this reading are sorted first.
 */
data class LookupOptions(
    val maxResults: Int = 16,
    val scanLength: Int = 16,
    val frequencyDictionary: String? = null,
    val frequencyOrder: FrequencyOrder = FrequencyOrder.ASCENDING,
    val primaryReading: String? = null,
)

enum class FrequencyOrder {
    /** Lower values are more frequent (rank-based dictionaries). */
    ASCENDING,

    /** Higher values are more frequent (occurrence-based dictionaries). */
    DESCENDING,

    DISABLED,
}

/** Result of a successful import. [directory] is where the engine placed the converted dictionary. */
data class ImportedDictionary(
    val directory: File,
    val metadata: DictionaryMetadata,
)

/** Fields of a Yomitan `index.json` plus entry counts. */
data class DictionaryMetadata(
    val title: String,
    val revision: String,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val frequencyMode: String? = null,
    val isUpdatable: Boolean = false,
    val indexUrl: String? = null,
    val downloadUrl: String? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val termCount: Long = 0,
    val frequencyCount: Long = 0,
    val pitchCount: Long = 0,
    val kanjiCount: Long = 0,
    val mediaCount: Long = 0,
)

class DictionaryImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
