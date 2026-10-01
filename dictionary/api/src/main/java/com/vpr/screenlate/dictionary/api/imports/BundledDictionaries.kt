package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.dictionary.api.registry.decodeIndexText
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yomitan archives shipped in `assets/dictionaries/`, installed in file name order.
 *
 * Each archive is installed once, identified by name and size; a different archive of the same name shipped by a later
 * app version replaces the dictionary only while it is still the copy Screenlate installed from that archive (title
 * and revision, [markInstalled]). A copy the user put in its place (a file, the catalog, an update, a Yomitan
 * collection) stays, in any revision. An archive whose dictionary the user deleted is never installed again, whatever
 * version ships later ([markDeleted]), and neither is a newly shipped one whose dictionary the user already has from
 * elsewhere: theirs stays.
 * The numeric prefix is a slot: when a later version ships another dictionary in a slot whose earlier archive the
 * user already installed, the user keeps the earlier dictionary and the new one is not installed.
 */
@Singleton
class BundledDictionaries @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    data class Asset(val name: String, val size: Long) {
        val key: String get() = "$name:$size"

        /** File name without the ordering prefix and extension, e.g. `jitendex`. */
        val displayName: String get() = name.removeSuffix(".zip").substringAfter('-')
    }

    /** A dictionary's title and revision: an installed one, or the one Screenlate installed from an archive. */
    data class Copy(val title: String, val revision: String)

    /** Archives to install; [present] gives the installed dictionaries, of every kind. */
    suspend fun pending(present: suspend () -> List<Copy>): List<Asset> {
        val shipped = all()
        val prefs = dataStore.data.first()
        val installed = prefs[INSTALLED].orEmpty()
        var declined = prefs[DECLINED].orEmpty()
        val repair = prefs[REPAIR].orEmpty()
        var read: List<Copy>? = null
        suspend fun copies(): List<Copy> = read ?: present().also { read = it }
        // Versions before deletions were remembered: an archive installed then whose dictionary is gone was deleted.
        if (prefs[DECLINED_CHECKED] != true) {
            val titles = copies().map { it.title }
            val deleted = installedBefore(shipped, installed).filter { asset ->
                titleOf(asset)?.let { title -> titles.none { sameTitle(it, title) } } == true
            }
            declined = declined + deleted.map { it.name }
            dataStore.edit { it[DECLINED] = declined; it[DECLINED_CHECKED] = true }
        }
        val records = records(shipped, installed, prefs[RECORDS])
        val pending = pending(shipped, installed, declined).filterNot { it.name in repair }
        val owned = alreadyPresent(pending, installed, { copies().mapTo(hashSetOf()) { it.title } }, ::titleOf)
        val users = userCopies(pending, installed, records, ::copies, ::indexOf)
        if (owned.isNotEmpty() || users.isNotEmpty()) {
            dataStore.edit { prefs ->
                prefs[DECLINED] = prefs[DECLINED].orEmpty() + owned.map { it.name }
                // Looked at once per shipped version; the next one is compared with the user's copy again.
                prefs[INSTALLED] = withKeys(prefs[INSTALLED].orEmpty(), users)
            }
        }
        val install = pending - owned.toSet() - users.toSet()
        return shipped.filter { it in install || it.name in repair }
    }

    /** Remembers that the user deleted the bundled dictionary [title], so no later version installs it again. */
    suspend fun markDeleted(title: String) {
        val records = readRecords(dataStore.data.first()[RECORDS])
        // An archive is known by what was installed from it; one without a record is read.
        val names = all().filter { asset ->
            val installedTitle = records[asset.name]?.title ?: titleOf(asset)
            installedTitle != null && sameTitle(title, installedTitle)
        }.map { it.name }
        if (names.isEmpty()) return
        dataStore.edit { it[DECLINED] = it[DECLINED].orEmpty() + names }
    }

    suspend fun copy(asset: Asset, target: File) = withContext(Dispatchers.IO) {
        context.assets.open("$ASSET_DIR/${asset.name}").use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    /** Records [asset] as installed, with the [title] and [revision] the dictionary got from it. */
    suspend fun markInstalled(asset: Asset, title: String, revision: String) {
        dataStore.edit { prefs ->
            prefs[INSTALLED] = withKeys(prefs[INSTALLED].orEmpty(), listOf(asset))
            prefs[RECORDS] = writeRecords(readRecords(prefs[RECORDS]) + (asset.name to Copy(title, revision)))
            prefs[REPAIR] = prefs[REPAIR].orEmpty() - asset.name
        }
    }

    /** Marks [assets] for the next bundled install, which imports them again whatever is installed. */
    suspend fun markForRepair(assets: Collection<Asset>) {
        dataStore.edit { prefs -> prefs[REPAIR] = prefs[REPAIR].orEmpty() + assets.map { it.name } }
    }

    /** Forgets every install, deletion and record, so the next install brings every shipped archive as on a fresh install. */
    suspend fun reset() {
        dataStore.edit { prefs ->
            listOf(INSTALLED, DECLINED, RECORDS, REPAIR).forEach { prefs.remove(it) }
            // Nothing is left for the migration to check.
            prefs[DECLINED_CHECKED] = true
        }
    }

    /**
     * What Screenlate installed from each archive. One installed before this was recorded and shipped unchanged since
     * is filled in from the shipped file, which is what was installed.
     */
    private suspend fun records(shipped: List<Asset>, installed: Set<String>, stored: String?): Map<String, Copy> {
        val records = readRecords(stored)
        val unknown = shipped.filter { it.key in installed && it.name !in records }
        if (unknown.isEmpty()) return records
        val found = unknown.mapNotNull { asset -> indexOf(asset)?.let { asset.name to it } }.toMap()
        if (found.isEmpty()) return records
        dataStore.edit { prefs -> prefs[RECORDS] = writeRecords(found + readRecords(prefs[RECORDS])) }
        return found + records
    }

    /** Every shipped archive. */
    suspend fun all(): List<Asset> = withContext(Dispatchers.IO) {
        context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".zip") }
            .sorted()
            .map { name -> Asset(name, context.assets.openFd("$ASSET_DIR/$name").use { it.length }) }
    }

    /** The dictionary title in an archive's `index.json`. */
    suspend fun titleOf(asset: Asset): String? = indexOf(asset)?.title

    /** The dictionary title and revision in an archive's `index.json`, as an import stores them. */
    private suspend fun indexOf(asset: Asset): Copy? = withContext(Dispatchers.IO) {
        runCatching {
            ZipInputStream(context.assets.open("$ASSET_DIR/${asset.name}").buffered()).use { zip ->
                val index = generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == "index.json" }
                    ?.let { Json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject }
                    ?: return@use null
                val title = index["title"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val revision = index["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
                Copy(title, decodeIndexText(revision) ?: revision)
            }
        }.getOrNull()
    }

    /** Tag descriptions from the shipped archive whose title is [title]; null when none has it. */
    suspend fun tagNotesOf(title: String): Map<String, String>? = withContext(Dispatchers.IO) {
        val asset = all().firstOrNull { titleOf(it) == title } ?: return@withContext null
        ZipInputStream(context.assets.open("$ASSET_DIR/${asset.name}").buffered()).use(TagBanks::read)
    }

    internal companion object {
        private const val ASSET_DIR = "dictionaries"
        private val INSTALLED = stringSetPreferencesKey("bundled_dictionaries_installed")
        private val DECLINED = stringSetPreferencesKey("bundled_dictionaries_declined")
        private val DECLINED_CHECKED = booleanPreferencesKey("bundled_dictionaries_declined_checked")
        private val RECORDS = stringPreferencesKey("bundled_dictionaries_records")
        private val REPAIR = stringSetPreferencesKey("bundled_dictionaries_repair")

        /** [installed] keys with [assets] in place of other versions of the same archives. */
        private fun withKeys(installed: Set<String>, assets: List<Asset>): Set<String> {
            val names = assets.mapTo(hashSetOf()) { it.name }
            return installed.filterNot { it.substringBeforeLast(':') in names }.toSet() + assets.map { it.key }
        }

        /** Records as stored: `{"10-jmdict-english.zip": ["JMdict [2026-09-27]", "JMdict.2026-09-27"]}`. */
        fun readRecords(stored: String?): Map<String, Copy> {
            if (stored == null) return emptyMap()
            return runCatching {
                Json.parseToJsonElement(stored).jsonObject.mapValues { (_, value) ->
                    val (title, revision) = value.jsonArray.map { it.jsonPrimitive.content }
                    Copy(title, revision)
                }
            }.getOrDefault(emptyMap())
        }

        fun writeRecords(records: Map<String, Copy>): String = buildJsonObject {
            records.forEach { (name, copy) ->
                putJsonArray(name) {
                    add(JsonPrimitive(copy.title))
                    add(JsonPrimitive(copy.revision))
                }
            }
        }.toString()

        /**
         * [shipped] archives to install, given the `name:size` keys of the archives installed before and the names of
         * the archives whose dictionaries the user deleted.
         */
        fun pending(shipped: List<Asset>, installed: Set<String>, declined: Set<String> = emptySet()): List<Asset> {
            val shippedNames = shipped.map { it.name }.toSet()
            val installedNames = installed.map { it.substringBeforeLast(':') }
            val replacedSlots = installedNames.filter { it !in shippedNames }.map(::slot).toSet()
            return shipped.filter { asset ->
                asset.name !in declined &&
                    asset.key !in installed &&
                    (asset.name in installedNames || slot(asset.name) !in replacedSlots)
            }
        }

        /**
         * The archives of [pending] that no version of was installed before and whose dictionary is already there under
         * another revision or title ([sameTitle]), from the catalog, a file or a Yomitan collection: the user's copy stays.
         * [present] gives the installed titles and [titleOf] an archive's title; neither is read without new archives or
         * installed dictionaries, as on first launch.
         */
        suspend fun alreadyPresent(
            pending: List<Asset>,
            installed: Set<String>,
            present: suspend () -> Set<String>,
            titleOf: suspend (Asset) -> String?,
        ): List<Asset> {
            val installedNames = installed.mapTo(hashSetOf()) { it.substringBeforeLast(':') }
            val fresh = pending.filter { it.name !in installedNames }
            if (fresh.isEmpty()) return emptyList()
            val titles = present()
            if (titles.isEmpty()) return emptyList()
            return fresh.filter { asset -> titleOf(asset)?.let { title -> titles.any { sameTitle(it, title) } } == true }
        }

        /**
         * The archives of [pending] that replace an installed version but whose dictionary the user replaced with their
         * own copy, in any revision: no installed copy is still what Screenlate installed from the archive ([records])
         * or the archive itself ([indexOf]; installed, but not yet recorded). A copy installed before records were kept
         * counts as the user's, as nothing tells whose it is. An archive whose dictionary is gone is not among them and
         * is installed again; a deleted one is declined instead.
         */
        suspend fun userCopies(
            pending: List<Asset>,
            installed: Set<String>,
            records: Map<String, Copy>,
            present: suspend () -> List<Copy>,
            indexOf: suspend (Asset) -> Copy?,
        ): List<Asset> {
            val installedNames = installed.mapTo(hashSetOf()) { it.substringBeforeLast(':') }
            val updates = pending.filter { it.name in installedNames }
            if (updates.isEmpty()) return emptyList()
            val copies = present()
            return updates.filter { asset ->
                val record = records[asset.name]
                val shipped = if (record == null) indexOf(asset) else null
                val title = record?.title ?: shipped?.title ?: return@filter true
                val same = copies.filter { sameTitle(it.title, title) }
                if (same.isEmpty() || record in same) return@filter false
                (shipped ?: indexOf(asset)).let { it == null || it !in same }
            }
        }

        /** Shipped archives of which some version was installed before. */
        fun installedBefore(shipped: List<Asset>, installed: Set<String>): List<Asset> {
            val installedNames = installed.map { it.substringBeforeLast(':') }.toSet()
            return shipped.filter { it.name in installedNames }
        }

        /**
         * Whether the installed dictionary titled [installed] is the bundled one titled [bundled] in another revision.
         * Older builds of English dictionaries name the language: `KANJIDIC (English)` for `KANJIDIC [2026-270]`.
         */
        fun sameTitle(installed: String, bundled: String): Boolean = ownKey(installed) == ownKey(bundled)

        private fun ownKey(title: String): String = dictionaryKey(title).removeSuffix(" (english)")

        private fun slot(name: String): String = name.substringBefore('-')
    }
}
