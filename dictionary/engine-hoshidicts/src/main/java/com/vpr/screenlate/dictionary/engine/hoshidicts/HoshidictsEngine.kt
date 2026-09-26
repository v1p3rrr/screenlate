package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.DictionaryMetadata
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [DictionaryEngine] backed by hoshidicts. One native session holds the loaded dictionaries. */
@Singleton
class HoshidictsEngine @Inject constructor() : DictionaryEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var handle = 0L

    override suspend fun import(archive: File, outputDir: File): ImportedDictionary = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val raw = try {
            HoshidictsNative.importDictionary(
                archive.absolutePath.encodeToByteArray(),
                outputDir.absolutePath.encodeToByteArray(),
                false,
            )
        } catch (e: RuntimeException) {
            throw DictionaryImportException(e.message ?: "Import failed", e)
        }
        val result = json.decodeFromString<NativeImportResult>(raw.decodeToString())
        if (!result.success) throw DictionaryImportException(result.error.ifEmpty { "Import failed" })
        ImportedDictionary(
            directory = File(outputDir, result.title),
            metadata = result.summary.toMetadata(result.title),
        )
    }

    override suspend fun load(dictionaries: DictionarySet) = session { handle ->
        HoshidictsNative.load(
            handle,
            dictionaries.terms.toPaths(),
            dictionaries.frequencies.toPaths(),
            dictionaries.pitches.toPaths(),
            dictionaries.kanji.toPaths(),
        )
    }

    override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> = session { handle ->
        val frequencyDictionary = options.frequencyDictionary
            ?.takeIf { options.frequencyOrder != FrequencyOrder.DISABLED }
        val raw = HoshidictsNative.lookup(
            handle = handle,
            text = text.encodeToByteArray(),
            maxResults = options.maxResults,
            scanLength = options.scanLength,
            frequencyDictionary = frequencyDictionary?.encodeToByteArray(),
            frequencyOrder = if (frequencyDictionary == null) ORDER_DISABLED else options.frequencyOrder.toNative(),
            primaryReading = options.primaryReading?.encodeToByteArray(),
        )
        json.decodeFromString<List<LookupResult>>(raw.decodeToString())
    }

    override suspend fun styles(): List<DictionaryStyle> = session { handle ->
        json.decodeFromString<List<DictionaryStyle>>(HoshidictsNative.styles(handle).decodeToString())
    }

    override suspend fun media(dictionary: String, path: String): ByteArray? = session { handle ->
        HoshidictsNative.media(handle, dictionary.encodeToByteArray(), path.encodeToByteArray())
    }

    override suspend fun kanji(character: String): KanjiResult = session { handle ->
        json.decodeFromString<KanjiResult>(HoshidictsNative.kanji(handle, character.encodeToByteArray()).decodeToString())
    }

    private suspend fun <T> session(block: (Long) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle == 0L) handle = HoshidictsNative.create()
            block(handle)
        }
    }

    private fun List<File>.toPaths(): Array<ByteArray> = map { it.absolutePath.encodeToByteArray() }.toTypedArray()

    private fun FrequencyOrder.toNative(): Int = when (this) {
        FrequencyOrder.ASCENDING -> ORDER_ASCENDING
        FrequencyOrder.DESCENDING -> ORDER_DESCENDING
        FrequencyOrder.DISABLED -> ORDER_DISABLED
    }

    private companion object {
        // Values of hoshidicts' LookupFrequencyOrder.
        const val ORDER_ASCENDING = 1
        const val ORDER_DESCENDING = 2
        const val ORDER_DISABLED = 3
    }
}

@Serializable
private data class NativeImportResult(
    val success: Boolean,
    val title: String = "",
    val error: String = "",
    val summary: NativeSummary = NativeSummary(),
)

@Serializable
private data class NativeSummary(
    val revision: String = "",
    val isUpdatable: Boolean? = null,
    val indexUrl: String? = null,
    val downloadUrl: String? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val frequencyMode: String? = null,
    val counts: NativeCounts = NativeCounts(),
) {
    fun toMetadata(title: String) = DictionaryMetadata(
        title = title,
        revision = revision,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        frequencyMode = frequencyMode,
        isUpdatable = isUpdatable == true,
        indexUrl = indexUrl,
        downloadUrl = downloadUrl,
        author = author,
        url = url,
        description = description,
        attribution = attribution,
        termCount = counts.terms.total,
        frequencyCount = counts.termMeta["freq"] ?: 0,
        pitchCount = (counts.termMeta["pitch"] ?: 0) + (counts.termMeta["ipa"] ?: 0),
        kanjiCount = counts.kanji.total,
        mediaCount = counts.media.total,
    )
}

@Serializable
private data class NativeCounts(
    val terms: NativeCount = NativeCount(),
    val termMeta: Map<String, Long> = emptyMap(),
    val kanji: NativeCount = NativeCount(),
    val media: NativeCount = NativeCount(),
)

@Serializable
private data class NativeCount(val total: Long = 0)
