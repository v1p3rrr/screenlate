package com.vpr.screenlate.dictionary.api.catalog

import android.content.Context
import android.util.Log
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dictionary the app can download. Adding a dictionary is one entry in `catalog/dictionaries.json`.
 *
 * @property installedTitle title prefix of the installed dictionary, used when it has no [indexUrl].
 * @property resolveLatest ask [indexUrl] for the current `downloadUrl` before downloading.
 * @property description text per language code; `en` is the fallback.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    val title: String,
    val installedTitle: String,
    val kind: DictionaryKind,
    val sourceLanguage: String,
    val targetLanguage: String? = null,
    val description: Map<String, String> = emptyMap(),
    val indexUrl: String? = null,
    val downloadUrl: String,
    val resolveLatest: Boolean = false,
    val sizeMb: Int = 0,
    val license: String = "",
    val homepage: String = "",
) {
    fun description(locale: Locale = Locale.getDefault()): String =
        description[locale.language] ?: description["en"].orEmpty()

    /** Whether [dictionary] is an installed copy of this entry, whatever its revision. */
    fun matches(dictionary: DictionaryEntity): Boolean =
        (indexUrl != null && dictionary.indexUrl == indexUrl) || dictionary.title.startsWith(installedTitle)
}

@Serializable
private data class CatalogDocument(
    val format: Int,
    val dictionaries: List<CatalogEntry>,
)

/**
 * The download catalog. The canonical file is this module's `assets/catalog/dictionaries.json`; the app fetches
 * its current version from the repository so new dictionaries appear without an app update, and falls back to
 * the last fetched copy, then to the bundled one.
 */
@Singleton
class DictionaryCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "catalog.json")

    /** Emits the cached or bundled catalog at once, then the fetched one if it differs. */
    fun entries(): Flow<List<CatalogEntry>> = flow {
        val local = withContext(Dispatchers.IO) { cached() ?: bundled() }
        emit(local)
        val remote = withContext(Dispatchers.IO) { fetch() }
        if (remote != null && remote != local) emit(remote)
    }

    private fun bundled(): List<CatalogEntry> =
        context.assets.open(ASSET).use { parse(it.readBytes().decodeToString()) } ?: emptyList()

    private fun cached(): List<CatalogEntry>? =
        runCatching { if (cacheFile.exists()) parse(cacheFile.readText()) else null }.getOrNull()

    private fun fetch(): List<CatalogEntry>? = runCatching {
        httpClient.newCall(Request.Builder().url(REMOTE_URL).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body.string()
            parse(text)?.also { cacheFile.writeText(text) }
        }
    }.onFailure { Log.i(TAG, "Catalog fetch failed: ${it.message}") }.getOrNull()

    /** Null when the document is malformed or uses a format this app version does not understand. */
    private fun parse(text: String): List<CatalogEntry>? = runCatching {
        json.decodeFromString<CatalogDocument>(text).takeIf { it.format == FORMAT }?.dictionaries
    }.getOrNull()

    private companion object {
        const val TAG = "DictionaryCatalog"
        const val ASSET = "catalog/dictionaries.json"
        const val FORMAT = 1
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/v1p3rrr/screenlate/main/dictionary/api/src/main/assets/$ASSET"
    }
}
