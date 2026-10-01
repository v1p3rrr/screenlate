package com.vpr.screenlate.dictionary.api.registry

import android.util.Log
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.catalog.outdatedCopies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A newer revision of an installed dictionary, found through its Yomitan `indexUrl`, or the current build of a catalog
 * entry for an old copy that cannot update itself.
 */
data class DictionaryUpdate(
    val dictionary: DictionaryEntity,
    val revision: String,
    val downloadUrl: String,
)

/** Checks installed dictionaries for updates the way Yomitan does: the index file's revision differs. */
@Singleton
class DictionaryUpdates @Inject constructor(
    private val repository: DictionaryRepository,
    private val httpClient: OkHttpClient,
    private val catalog: DictionaryCatalog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Updates for every updatable dictionary and the current builds for old copies the catalog knows
     * ([outdatedCopies]); dictionaries whose index cannot be read are skipped.
     */
    suspend fun check(): List<DictionaryUpdate> = coroutineScope {
        val dictionaries = repository.getAll()
        val updatable = dictionaries.filter { it.updatesItself }
        val outdated = outdatedCopies(withContext(Dispatchers.IO) { catalog.local() }, dictionaries)
        val updates = updatable.map { dictionary ->
            async { check(dictionary, dictionary.indexUrl.orEmpty(), dictionary.downloadUrl, outdated = false) }
        }
        val replacements = outdated.map { (dictionary, entry) ->
            async { check(dictionary, entry.indexUrl.orEmpty(), entry.downloadUrl, outdated = true) }
        }
        (updates + replacements)
            .awaitAll()
            .filterNotNull()
            .also { Log.i(TAG, "Checked ${updatable.size} dictionaries and ${outdated.size} old copies, ${it.size} updates") }
    }

    private suspend fun check(
        dictionary: DictionaryEntity,
        indexUrl: String,
        fallbackDownloadUrl: String?,
        outdated: Boolean,
    ): DictionaryUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val index = IndexMoves.follow(indexUrl) { url -> fetch(dictionary, url) } ?: return@runCatching null
            val downloadUrl = index.downloadUrl ?: fallbackDownloadUrl ?: return@runCatching null
            // An old copy is a different build: its revision cannot be compared with the current one.
            if (index.revision.isNotBlank() && (outdated || index.revision != dictionary.revision)) {
                Log.i(TAG, "Update for ${dictionary.title}: ${dictionary.revision} → ${index.revision}")
                DictionaryUpdate(dictionary, index.revision, downloadUrl)
            } else {
                null
            }
        }.onFailure { Log.i(TAG, "Update check for ${dictionary.title} failed: ${it.message}") }.getOrNull()
    }

    private fun fetch(dictionary: DictionaryEntity, url: String): RemoteIndex? =
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.i(TAG, "Update check for ${dictionary.title} failed: HTTP ${response.code}")
                return@use null
            }
            json.decodeFromString<RemoteIndex>(response.body.string())
        }

    private companion object {
        const val TAG = "DictionaryUpdates"
    }
}

/** Whether the dictionary can be checked for updates through its own Yomitan `indexUrl`. */
val DictionaryEntity.updatesItself: Boolean
    get() = isUpdatable && !indexUrl.isNullOrBlank()

/** The fields of a remote Yomitan index an update check reads. */
@Serializable
internal data class RemoteIndex(val revision: String = "", val downloadUrl: String? = null, val indexUrl: String? = null)

/**
 * A dictionary that moved keeps its old index online with the new address in `indexUrl` (Wiktionary moved from
 * kty-* to wty-* that way); its revision there is frozen, so the check follows the new address once.
 */
internal object IndexMoves {
    fun follow(url: String, fetch: (String) -> RemoteIndex?): RemoteIndex? {
        val first = fetch(url) ?: return null
        val moved = first.indexUrl?.takeIf { it.isNotBlank() && it != url } ?: return first
        return runCatching { fetch(moved) }.getOrNull() ?: first
    }
}
