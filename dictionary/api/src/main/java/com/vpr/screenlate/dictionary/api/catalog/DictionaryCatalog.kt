package com.vpr.screenlate.dictionary.api.catalog

import android.content.Context
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dictionary the app can download. The list lives in `assets/catalog/dictionaries.json`; adding a dictionary
 * is one entry there.
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

@Singleton
class DictionaryCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: List<CatalogEntry>? = null

    suspend fun entries(): List<CatalogEntry> = cached ?: withContext(Dispatchers.IO) {
        context.assets.open(ASSET).use { input ->
            json.decodeFromString<List<CatalogEntry>>(input.readBytes().decodeToString())
        }.also { cached = it }
    }

    private companion object {
        const val ASSET = "catalog/dictionaries.json"
    }
}
