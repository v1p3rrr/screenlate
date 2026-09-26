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
        repository.getAll()
            .filter { it.isUpdatable && !it.indexUrl.isNullOrBlank() }
            .map { dictionary -> async { check(dictionary) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun check(dictionary: DictionaryEntity): DictionaryUpdate? = withContext(Dispatchers.IO) {
        val indexUrl = dictionary.indexUrl ?: return@withContext null
        runCatching {
            httpClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val index = json.decodeFromString<RemoteIndex>(response.body.string())
                val downloadUrl = index.downloadUrl ?: dictionary.downloadUrl ?: return@use null
                if (index.revision.isNotBlank() && index.revision != dictionary.revision) {
                    DictionaryUpdate(dictionary, index.revision, downloadUrl)
                } else {
                    null
                }
            }
        }.onFailure { Log.i(TAG, "Update check for ${dictionary.title} failed: ${it.message}") }.getOrNull()
    }

    @Serializable
    private data class RemoteIndex(val revision: String = "", val downloadUrl: String? = null)

    private companion object {
        const val TAG = "DictionaryUpdates"
    }
}
