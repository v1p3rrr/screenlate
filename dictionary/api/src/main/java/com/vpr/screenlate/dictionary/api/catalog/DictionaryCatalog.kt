package com.vpr.screenlate.dictionary.api.catalog

import android.content.Context
import android.util.Log
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.updatesItself
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a catalog entry is for on the download screen. Without an explicit category an entry takes the one of its
 * [DictionaryKind].
 */
@Serializable
enum class CatalogCategory {
    MAIN,

    /** Inflected forms, for languages whose forms come only from dictionaries. */
    FORMS,
    FREQUENCY,
    PRONUNCIATION,

    /** Short word-for-word translations without definitions. */
    GLOSSARY,
    CHARACTERS,
    ;

    companion object {
        fun of(kind: DictionaryKind): CatalogCategory = when (kind) {
            DictionaryKind.TERM -> MAIN
            DictionaryKind.FREQUENCY -> FREQUENCY
            DictionaryKind.PITCH -> PRONUNCIATION
            DictionaryKind.KANJI -> CHARACTERS
        }
    }
}

/**
 * A dictionary the app can download. Adding a dictionary is one entry in `catalog/dictionaries-v2.json`; the
 * Wiktionary entries there are written by `scripts/catalog/generate.mjs`.
 *
 * @property title the entry's own name, also the name of its download task; [displayTitle] is what the user sees.
 * @property installedTitle title prefix of the installed dictionary, used when it has no [indexUrl].
 * @property formerTitles title prefixes the dictionary had before upstream renamed it; copies installed then still match.
 * @property oldTitles exact titles of installed copies, matched whole or followed by ` [` (a dated build), e.g. `JMdict`,
 *   which [installedTitle] `JMdict [` leaves out so as not to match `JMdict (Russian)`.
 * @property titles the title per locale key ([localeKey]), with the placeholders of [description].
 * @property description text per locale key, `en` the fallback; `{source}` and `{target}` stand for the language names,
 *   `{src}` and `{tgt}` for their codes.
 * @property template the catalog template that gave [titles] and [description], if any.
 * @property recommended ticked on the download screen: one main dictionary per gloss language, and the optional
 *   dictionaries ticked by default.
 * @property resolveLatest ask [indexUrl] for the current `downloadUrl` before downloading.
 * @property downloadSize archive size in bytes; catalogs of format 1 give [sizeMb] instead.
 * @property installedSize measured size after import in bytes; [installedBytes] estimates it otherwise.
 * @property nonCommercial the license forbids commercial use.
 * @property sha256 checksum of the archive, where the project hosts the file.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    val title: String,
    val installedTitle: String? = null,
    val formerTitles: List<String> = emptyList(),
    val oldTitles: List<String> = emptyList(),
    val kind: DictionaryKind,
    @SerialName("category") private val declaredCategory: CatalogCategory? = null,
    val sourceLanguage: String,
    val targetLanguage: String? = null,
    val titles: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val template: String? = null,
    val recommended: Boolean = false,
    val indexUrl: String? = null,
    val downloadUrl: String,
    val resolveLatest: Boolean = false,
    val downloadSize: Long = 0,
    val installedSize: Long? = null,
    val sizeMb: Int = 0,
    val license: String = "",
    val nonCommercial: Boolean = false,
    val homepage: String = "",
    val sha256: String? = null,
) {
    val category: CatalogCategory get() = declaredCategory ?: CatalogCategory.of(kind)

    val downloadBytes: Long get() = if (downloadSize > 0) downloadSize else sizeMb * MIB

    /** The space the dictionary takes once imported: measured, or a cautious guess from the archive size. */
    val installedBytes: Long get() = installedSize ?: (downloadBytes * INSTALLED_PER_DOWNLOADED)

    fun displayTitle(locale: Locale = Locale.getDefault()): String =
        localized(titles, locale)?.let { fill(it, locale) } ?: title

    fun description(locale: Locale = Locale.getDefault()): String =
        (localized(description, locale) ?: description["en"])?.let { fill(it, locale) }.orEmpty()

    private fun localized(texts: Map<String, String>, locale: Locale): String? =
        texts[localeKey(locale)] ?: texts[locale.language]

    private fun fill(text: String, locale: Locale): String {
        if ('{' !in text) return text
        fun name(code: String?) = code?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }.orEmpty()
        return text.replace("{source}", name(sourceLanguage))
            .replace("{target}", name(targetLanguage))
            .replace("{src}", sourceLanguage)
            .replace("{tgt}", targetLanguage.orEmpty())
    }

    /**
     * Whether [dictionary] is an installed copy of this entry, whatever its revision. Only the same kind counts: title
     * prefixes may overlap across kinds (`Jiten` for `Jitendex`).
     */
    fun matches(dictionary: DictionaryEntity): Boolean =
        dictionary.kind == kind && matches(dictionary.indexUrl, dictionary.title)

    /** Whether a dictionary with this index URL and title is a copy of this entry; the caller compares the kind. */
    fun matches(indexUrl: String?, title: String): Boolean =
        (this.indexUrl != null && indexUrl == this.indexUrl) || installedTitle?.let(title::startsWith) == true ||
            formerTitles.any(title::startsWith) || oldTitles.any { title == it || title.startsWith("$it [") }

    /**
     * The copy among [copies] (copies of this entry) to replace with this entry's current build: an old one that cannot
     * update itself, as long as no copy can and this entry has an update address to take the current build from.
     */
    fun outdatedCopy(copies: List<DictionaryEntity>): DictionaryEntity? =
        copies.takeIf { indexUrl != null && it.none(DictionaryEntity::updatesItself) }?.firstOrNull()
}

private const val MIB = 1024L * 1024L

/** Imports measured so far stay below it; the free space check prefers a guess too high to one too low. */
private const val INSTALLED_PER_DOWNLOADED = 3L

/** The key of catalog texts for [locale]: the language, or `zh-Hant` for Chinese in traditional characters. */
fun localeKey(locale: Locale): String = when {
    locale.language != "zh" -> locale.language
    locale.script == "Hant" || (locale.script.isEmpty() && locale.country in TRADITIONAL_CHINESE_REGIONS) -> "zh-Hant"
    else -> "zh"
}

private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

/** Installed copies to replace with the current build of a catalog entry, see [CatalogEntry.outdatedCopy]. */
fun outdatedCopies(entries: List<CatalogEntry>, dictionaries: List<DictionaryEntity>): Map<DictionaryEntity, CatalogEntry> =
    entries.mapNotNull { entry ->
        entry.outdatedCopy(dictionaries.filter(entry::matches))?.let { it to entry }
    }.toMap()

/** The download catalog: its entries and the categories a language cannot work without. */
data class Catalog(
    val entries: List<CatalogEntry>,
    private val mandatory: Map<String, Set<CatalogCategory>> = emptyMap(),
) {
    /** Categories [language] needs at least one dictionary of: the main dictionary, and more for some languages. */
    fun mandatory(language: String): Set<CatalogCategory> = mandatory[language] ?: setOf(CatalogCategory.MAIN)
}

@Serializable
private data class CatalogTemplate(
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
)

/** Entries stay JSON until each is read, so an entry this version cannot read leaves out only itself. */
@Serializable
private data class CatalogDocument(
    val format: Int,
    val mandatory: Map<String, List<String>> = emptyMap(),
    val templates: Map<String, CatalogTemplate> = emptyMap(),
    val dictionaries: List<JsonObject>,
)

/**
 * The download catalog. The canonical file is this module's `assets/catalog/dictionaries-v2.json`; the app fetches
 * its current version from the repository so new dictionaries appear without an app update, and falls back to
 * the last fetched copy, then to the bundled one. `dictionaries.json` next to it is the format 1 copy that older
 * versions fetch.
 */
@Singleton
class DictionaryCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val cacheFile = File(context.filesDir, "catalog-v2.json")

    /** Emits the cached or bundled catalog at once, then the fetched one if it differs. */
    fun catalog(): Flow<Catalog> = flow {
        val local = withContext(Dispatchers.IO) { localCatalog() }
        emit(local)
        val remote = withContext(Dispatchers.IO) { fetch() }
        if (remote != null && remote != local) emit(remote)
    }

    fun entries(): Flow<List<CatalogEntry>> = catalog().map { it.entries }

    /** The last fetched or the bundled catalog, without network access. */
    fun localCatalog(): Catalog = cached() ?: bundled()

    fun local(): List<CatalogEntry> = localCatalog().entries

    private fun bundled(): Catalog =
        context.assets.open(ASSET).use { parse(it.readBytes().decodeToString()) } ?: Catalog(emptyList())

    private fun cached(): Catalog? =
        runCatching { if (cacheFile.exists()) parse(cacheFile.readText()) else null }.getOrNull()

    private fun fetch(): Catalog? = runCatching {
        httpClient.newCall(Request.Builder().url(REMOTE_URL).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body.string()
            parse(text)?.also {
                cacheFile.writeText(text)
                File(context.filesDir, FORMAT_1_CACHE).delete()
            }
        }
    }.onFailure { Log.i(TAG, "Catalog fetch failed: ${it.message}") }.getOrNull()

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Null when the document is malformed or uses a format this app version does not understand. Format 1 has no
         * templates and gives sizes in whole megabytes.
         */
        fun parse(text: String): Catalog? = runCatching {
            val document = json.decodeFromString<CatalogDocument>(text).takeIf { it.format in FORMATS } ?: return null
            val entries = document.dictionaries.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement<CatalogEntry>(element) }
                    .onFailure { Log.i(TAG, "Catalog entry skipped: ${it.message}") }
                    .getOrNull()
                    ?.let { entry -> withTemplate(entry, document.templates) }
            }
            val mandatory = document.mandatory.mapValues { (_, names) ->
                names.mapNotNullTo(linkedSetOf()) { name -> CatalogCategory.entries.firstOrNull { it.name == name } }
            }
            Catalog(entries, mandatory)
        }.getOrNull()

        private fun withTemplate(entry: CatalogEntry, templates: Map<String, CatalogTemplate>): CatalogEntry {
            val template = entry.template?.let(templates::get) ?: return entry
            return entry.copy(titles = template.title + entry.titles, description = template.description + entry.description)
        }

        private const val TAG = "DictionaryCatalog"
        private const val ASSET = "catalog/dictionaries-v2.json"
        private val FORMATS = 1..2

        /** Where versions before format 2 kept their copy of the catalog. */
        private const val FORMAT_1_CACHE = "catalog.json"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/v1p3rrr/screenlate/main/dictionary/api/src/main/assets/$ASSET"
    }
}
