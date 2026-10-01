package com.vpr.screenlate.dictionary.api.registry

import android.util.Log
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

/** A newer revision of an installed dictionary, found through its Yomitan `indexUrl`. */
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
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Updates for every updatable dictionary; dictionaries whose index cannot be read are skipped. */
    suspend fun check(): List<DictionaryUpdate> = coroutineScope {
        val updatable = repository.getAll().filter { it.isUpdatable && !it.indexUrl.isNullOrBlank() }
        updatable
            .map { dictionary -> async { check(dictionary) } }
            .awaitAll()
            .filterNotNull()
            .also { Log.i(TAG, "Checked ${updatable.size} dictionaries, ${it.size} updates") }
    }

    private suspend fun check(dictionary: DictionaryEntity): DictionaryUpdate? = withContext(Dispatchers.IO) {
        val indexUrl = dictionary.indexUrl ?: return@withContext null
        runCatching {
            val index = IndexMoves.follow(indexUrl) { url -> fetch(dictionary, url) } ?: return@runCatching null
            val downloadUrl = index.downloadUrl ?: dictionary.downloadUrl ?: return@runCatching null
            if (index.revision.isNotBlank() && index.revision != dictionary.revision) {
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
