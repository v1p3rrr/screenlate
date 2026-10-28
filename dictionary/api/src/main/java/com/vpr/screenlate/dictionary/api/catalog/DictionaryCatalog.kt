package com.vpr.screenlate.dictionary.api.catalog

import android.content.Context
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.languageName
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.isFormsOnly
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
import kotlin.math.roundToLong

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

    /** An on-device recognition model, for a script ML Kit has no model for; not a dictionary. */
    OCR_MODEL,
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
 * @property sourceLanguage a dictionary's [Language.dictionaryCode], which languages reading the same dictionaries
 *   share (Croatian and Serbian); a model's [Language.code], as each language reads with its own models ([isFor]).
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
 * @property installedSize measured size after import in bytes, from the entry or from `catalog/installed-sizes.json`
 *   ([MeasuredSize]); [installedBytes] estimates it otherwise.
 * @property nonCommercial the license forbids commercial use.
 * @property sha256 checksum of the archive, where the project hosts the file; required for a model and for a file
 *   the app builds a dictionary from.
 * @property kind the dictionary's kind; null for a model ([CatalogCategory.OCR_MODEL]).
 * @property model a model's id in the OCR model store; entries of several languages may share one model, such as a
 *   text detector.
 * @property engine the on-device engine that reads [model].
 * @property file where a model downloaded as a single file is kept inside the model's directory, such as
 *   `tessdata/rus.traineddata`, the layout its engine reads; a zip archive is unpacked instead.
 * @property requires ids of other models [model] reads only together with, such as a text detector.
 * @property version [model]'s version; an installed copy of another version is replaced. For [build], the version of
 *   the data the downloaded file holds.
 * @property build how the app builds the dictionary from the downloaded file instead of importing it as it is:
 *   [BUILD_WORDFREQ] for one of wordfreq's word lists ([com.vpr.screenlate.dictionary.api.imports.WordfreqDictionary]),
 *   [BUILD_CORPUS_COUNTS] for a corpus' word counts ([com.vpr.screenlate.dictionary.api.imports.CorpusCountsDictionary]);
 *   [BUILD_FORMS] imports only the inflected forms of the downloaded dictionary, the same file another entry installs
 *   whole.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    val title: String,
    val installedTitle: String? = null,
    val formerTitles: List<String> = emptyList(),
    val oldTitles: List<String> = emptyList(),
    val kind: DictionaryKind? = null,
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
    val model: String? = null,
    val engine: String? = null,
    val file: String? = null,
    val requires: List<String> = emptyList(),
    val version: String? = null,
    val build: String? = null,
) {
    val category: CatalogCategory get() = declaredCategory ?: kind?.let(CatalogCategory::of) ?: CatalogCategory.MAIN

    val isModel: Boolean get() = category == CatalogCategory.OCR_MODEL

    /** Whether the entry is one of [language]'s: by its own code for a model, by its dictionaries' code otherwise. */
    fun isFor(language: Language): Boolean = sourceLanguage == if (isModel) language.code else language.dictionaryCode

    /**
     * Whether lookups in [language] read the entry once installed: one of [language]'s ([isFor]), or a dictionary with
     * definitions for the language its lookups fall back to (`LanguageSupport.fallbackLanguage`, Czech for Slovak).
     */
    fun isReadBy(language: Language): Boolean =
        isFor(language) || (!isModel && kind == DictionaryKind.TERM && language.support.fallbackLanguage?.let(::isFor) == true)

    /**
     * A dictionary names its kind; a model its id, engine, version and checksum; a built dictionary a known [build],
     * the version and the checksum. The forms of a dictionary come from its upstream file, which has no checksum.
     */
    internal val isComplete: Boolean
        get() = when {
            isModel -> model != null && engine != null && version != null && sha256 != null
            build == BUILD_FORMS -> kind != null
            build != null -> build in BUILDS && kind != null && version != null && sha256 != null
            else -> kind != null
        }

    val downloadBytes: Long get() = if (downloadSize > 0) downloadSize else sizeMb * MIB

    /** The archive comes with the app ([ASSET_URL_PREFIX]): installing it downloads nothing. */
    val isBundled: Boolean get() = downloadUrl.startsWith(ASSET_URL_PREFIX)

    /**
     * The space the dictionary takes once imported, as the download screen shows it: measured, or the typical share
     * of the archive size ([InstalledShare]). A model is stored as downloaded or unpacked, which compressed weights
     * barely shrink.
     */
    val installedBytes: Long get() = installedSize ?: estimatedInstalledBytes(cautious = false)

    /**
     * [installedBytes] for the free space check: measured with a quarter more for later releases, or above every
     * import measured so far.
     */
    val installedBytesAtMost: Long get() = installedSize?.let { it + it / 4 } ?: estimatedInstalledBytes(cautious = true)

    private fun estimatedInstalledBytes(cautious: Boolean): Long {
        if (isModel) return downloadBytes
        if (build == BUILD_WORDFREQ || build == BUILD_CORPUS_COUNTS) {
            if (cautious) return BUILT_INSTALLED_AT_MOST
            val share = if (build == BUILD_WORDFREQ) WORDFREQ_INSTALLED_PER_DOWNLOADED else CORPUS_INSTALLED_PER_DOWNLOADED
            return minOf(downloadBytes * share, BUILT_INSTALLED_TYPICAL)
        }
        val share = when (kind) {
            DictionaryKind.KANJI -> InstalledShare.KANJI
            DictionaryKind.FREQUENCY -> InstalledShare.FREQUENCY
            DictionaryKind.PITCH -> InstalledShare.PITCH
            else -> InstalledShare.TERM
        }
        return (downloadBytes * if (cautious) share.atMost else share.typical).roundToLong()
    }

    fun displayTitle(locale: Locale = Locale.getDefault()): String =
        localized(titles, locale)?.let { fill(it, locale) } ?: title

    fun description(locale: Locale = Locale.getDefault()): String =
        (localized(description, locale) ?: description["en"])?.let { fill(it, locale) }.orEmpty()

    private fun localized(texts: Map<String, String>, locale: Locale): String? =
        texts[localeKey(locale)] ?: texts[locale.language]

    private fun fill(text: String, locale: Locale): String {
        if ('{' !in text) return text
        fun name(code: String?) = code?.let { languageName(it, locale) }.orEmpty()
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

    /**
     * Whether a dictionary with this index URL and title is a copy of this entry; the caller compares the kind. The
     * forms of a dictionary ([BUILD_FORMS]) share its index URL; their title tells them apart ([isFormsOnly]).
     */
    fun matches(indexUrl: String?, title: String): Boolean =
        isFormsOnly(title) == (build == BUILD_FORMS) &&
            ((this.indexUrl != null && indexUrl == this.indexUrl) || installedTitle?.let(title::startsWith) == true ||
                formerTitles.any(title::startsWith) || oldTitles.any { title == it || title.startsWith("$it [") })

    /**
     * Whether this entry, the forms of a dictionary ([BUILD_FORMS]), is cut from [dictionary], an installed copy of the
     * whole dictionary, which holds the forms as well.
     */
    fun isCutFrom(dictionary: DictionaryEntity): Boolean =
        build == BUILD_FORMS && dictionary.kind == kind && indexUrl != null && dictionary.indexUrl == indexUrl &&
            !isFormsOnly(dictionary.title)

    /** Whether this entry, the forms of a dictionary ([BUILD_FORMS]), is cut from [entry], the whole dictionary. */
    fun isCutFrom(entry: CatalogEntry): Boolean =
        build == BUILD_FORMS && entry.build != BUILD_FORMS && entry.kind == kind && indexUrl != null &&
            entry.indexUrl == indexUrl

    /**
     * The copy among [copies] (copies of this entry) to replace with this entry's current build: an old one that cannot
     * update itself, as long as no copy can and this entry has an update address to take the current build from.
     */
    fun outdatedCopy(copies: List<DictionaryEntity>): DictionaryEntity? =
        copies.takeIf { indexUrl != null && it.none(DictionaryEntity::updatesItself) }?.firstOrNull()
}

private const val MIB = 1024L * 1024L

/**
 * [CatalogEntry.downloadUrl] prefix of an archive the app carries in its assets (the path follows): installing it copies
 * the archive and needs no network.
 */
const val ASSET_URL_PREFIX = "asset:///"

/** [CatalogEntry.build] of a frequency dictionary built from a wordfreq word list. */
const val BUILD_WORDFREQ = "wordfreq"

/** [CatalogEntry.build] of a frequency dictionary built from a corpus' word counts. */
const val BUILD_CORPUS_COUNTS = "corpus-counts"

/** [CatalogEntry.build] of the inflected forms of a dictionary, imported without its definitions. */
const val BUILD_FORMS = "forms"

private val BUILDS = setOf(BUILD_WORDFREQ, BUILD_CORPUS_COUNTS)

/**
 * Installed bytes per archive byte for an entry with no measured size, from 150 imports measured on a device:
 * [typical] near the median and the size-weighted mean, [atMost] above the largest. The free space check prefers a
 * guess too high to one too low.
 */
private enum class InstalledShare(val typical: Double, val atMost: Double) {
    /** Glossaries: 1.2-4.7, most near 2.2. */
    TERM(2.2, 5.0),
    KANJI(3.0, 6.0),

    /** Many short rows: 8.7-16. */
    FREQUENCY(10.0, 17.0),

    /** Pitch accents and IPA transcriptions: 7-14, most near 10. */
    PITCH(10.5, 15.0),
}

/**
 * A built frequency dictionary keeps a language's most frequent words (`WordfreqDictionary.DEFAULT_SIZE`), so a long
 * source takes no more than a shorter one: 1-14 MB measured, about 12 MB from a long list. A short wordfreq list takes
 * about 25 times its download, a corpus' counts about 5 times.
 */
private const val BUILT_INSTALLED_TYPICAL = 12L * MIB
private const val BUILT_INSTALLED_AT_MOST = 16L * MIB
private const val WORDFREQ_INSTALLED_PER_DOWNLOADED = 25L
private const val CORPUS_INSTALLED_PER_DOWNLOADED = 5L

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

    /** [mandatory] for an entry: its own code's, else its dictionaries' code's. */
    fun mandatory(language: Language): Set<CatalogCategory> = mandatory[language.code] ?: mandatory(language.dictionaryCode)
}

/**
 * A dictionary's size after import measured on a device, with the size of the archive it was imported from;
 * `scripts/catalog/installed_sizes.py` writes them into `catalog/installed-sizes.json`.
 */
@Serializable
data class MeasuredSize(val download: Long, val installed: Long)

@Serializable
private data class MeasuredSizes(val sizes: Map<String, MeasuredSize> = emptyMap())

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

    private val measuredSizes: Map<String, MeasuredSize> by lazy {
        runCatching { context.assets.open(SIZES_ASSET).use { parseSizes(it.readBytes().decodeToString()) } }
            .onFailure { Log.w(TAG, "Measured sizes unreadable: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyMap())
    }

    /** Emits the cached or bundled catalog at once, then the fetched one if it differs. */
    fun catalog(): Flow<Catalog> = flow {
        val local = withContext(Dispatchers.IO) { localCatalog() }
        emit(local)
        val remote = withContext(Dispatchers.IO) { fetch() }
        if (remote != null && remote != local) emit(remote)
    }

    fun entries(): Flow<List<CatalogEntry>> = catalog().map { it.entries }

    /** The last fetched or the bundled catalog, without network access; parsed again only when a fetch changed it. */
    fun localCatalog(): Catalog {
        val stamp = cacheFile.lastModified()
        parsed?.takeIf { it.first == stamp }?.let { return it.second }
        return (cached() ?: bundled()).also { parsed = stamp to it }
    }

    @Volatile
    private var parsed: Pair<Long, Catalog>? = null

    fun local(): List<CatalogEntry> = localCatalog().entries

    private fun bundled(): Catalog =
        context.assets.open(ASSET).use { parse(it.readBytes().decodeToString(), measuredSizes) } ?: Catalog(emptyList())

    private fun cached(): Catalog? =
        runCatching { if (cacheFile.exists()) parse(cacheFile.readText(), measuredSizes) else null }.getOrNull()

    private fun fetch(): Catalog? = runCatching {
        httpClient.newCall(Request.Builder().url(REMOTE_URL).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body.string()
            parse(text, measuredSizes)?.also {
                cacheFile.writeText(text)
                File(context.filesDir, FORMAT_1_CACHE).delete()
            }
        }
    }.onFailure { Log.i(TAG, "Catalog fetch failed: ${it.message}") }.getOrNull()

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Null when the document is malformed or uses a format this app version does not understand. Format 1 has no
         * templates and gives sizes in whole megabytes. An entry without its own size after import takes the one in
         * [measured].
         */
        fun parse(text: String, measured: Map<String, MeasuredSize> = emptyMap()): Catalog? = runCatching {
            val document = json.decodeFromString<CatalogDocument>(text).takeIf { it.format in FORMATS } ?: return null
            val entries = document.dictionaries.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement<CatalogEntry>(element) }
                    .onFailure { Log.i(TAG, "Catalog entry skipped: ${it.message}") }
                    .getOrNull()
                    ?.takeIf { entry -> entry.isComplete.also { if (!it) Log.i(TAG, "Catalog entry ${entry.id} skipped: incomplete") } }
                    ?.let { entry -> withMeasuredSize(withTemplate(entry, document.templates), measured) }
            }
            val mandatory = document.mandatory.mapValues { (_, names) ->
                names.mapNotNullTo(linkedSetOf()) { name -> CatalogCategory.entries.firstOrNull { it.name == name } }
            }
            Catalog(entries, mandatory)
        }.getOrNull()

        fun parseSizes(text: String): Map<String, MeasuredSize> = json.decodeFromString<MeasuredSizes>(text).sizes

        /** The measured size, in proportion to the entry's archive now: a later release may be larger. */
        private fun withMeasuredSize(entry: CatalogEntry, measured: Map<String, MeasuredSize>): CatalogEntry {
            if (entry.installedSize != null) return entry
            val size = measured[entry.id]?.takeIf { it.download > 0 } ?: return entry
            return entry.copy(installedSize = size.installed * entry.downloadBytes / size.download)
        }

        private fun withTemplate(entry: CatalogEntry, templates: Map<String, CatalogTemplate>): CatalogEntry {
            val template = entry.template?.let(templates::get) ?: return entry
            return entry.copy(titles = template.title + entry.titles, description = template.description + entry.description)
        }

        private const val TAG = "DictionaryCatalog"
        private const val ASSET = "catalog/dictionaries-v2.json"
        private const val SIZES_ASSET = "catalog/installed-sizes.json"
        private val FORMATS = 1..2

        /** Where versions before format 2 kept their copy of the catalog. */
        private const val FORMAT_1_CACHE = "catalog.json"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/v1p3rrr/screenlate/main/dictionary/api/src/main/assets/$ASSET"
    }
}
