package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yomitan archives shipped in `assets/dictionaries/`, installed in file name order.
 *
 * Each archive is installed once, identified by name and size; a different archive of the same name shipped by a later
 * app version is installed (replacing the same title). An archive whose dictionary the user deleted is never installed
 * again, whatever version ships later ([markDeleted]), and neither is a newly shipped one whose dictionary the user
 * already has from elsewhere (the catalog, a file, a Yomitan collection): theirs stays.
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

    /** Archives to install; [presentTitles] are the titles of the installed dictionaries, of every kind. */
    suspend fun pending(presentTitles: suspend () -> Set<String>): List<Asset> {
        val shipped = all()
        val prefs = dataStore.data.first()
        val installed = prefs[INSTALLED].orEmpty()
        var declined = prefs[DECLINED].orEmpty()
        var read: Set<String>? = null
        suspend fun present(): Set<String> = read ?: presentTitles().also { read = it }
        // Versions before deletions were remembered: an archive installed then whose dictionary is gone was deleted.
        if (prefs[DECLINED_CHECKED] != true) {
            val titles = present().mapTo(hashSetOf(), ::baseTitle)
            val deleted = installedBefore(shipped, installed).filter { asset ->
                titleOf(asset)?.let { baseTitle(it) !in titles } == true
            }
            declined = declined + deleted.map { it.name }
            dataStore.edit { it[DECLINED] = declined; it[DECLINED_CHECKED] = true }
        }
        val pending = pending(shipped, installed, declined)
        val owned = alreadyPresent(pending, installed, ::present, ::titleOf)
        if (owned.isEmpty()) return pending
        dataStore.edit { it[DECLINED] = it[DECLINED].orEmpty() + owned.map { asset -> asset.name } }
        return pending - owned.toSet()
    }

    /** Remembers that the user deleted the bundled dictionary [title], so no later version installs it again. */
    suspend fun markDeleted(title: String) {
        val names = all().filter { asset -> titleOf(asset)?.let(::baseTitle) == baseTitle(title) }.map { it.name }
        if (names.isEmpty()) return
        dataStore.edit { it[DECLINED] = it[DECLINED].orEmpty() + names }
    }

    suspend fun copy(asset: Asset, target: File) = withContext(Dispatchers.IO) {
        context.assets.open("$ASSET_DIR/${asset.name}").use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    suspend fun markInstalled(asset: Asset) {
        dataStore.edit { prefs ->
            val others = prefs[INSTALLED].orEmpty().filterNot { it.substringBeforeLast(':') == asset.name }
            prefs[INSTALLED] = others.toSet() + asset.key
        }
    }

    /** Marks every archive as not installed, so the next bundled install imports them again. */
    suspend fun forgetAll(assets: Collection<Asset>) {
        val names = assets.map { it.name }.toSet()
        dataStore.edit { prefs ->
            prefs[INSTALLED] = prefs[INSTALLED].orEmpty().filterNot { it.substringBeforeLast(':') in names }.toSet()
        }
    }

    /** Every shipped archive. */
    suspend fun all(): List<Asset> = withContext(Dispatchers.IO) {
        context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".zip") }
            .sorted()
            .map { name -> Asset(name, context.assets.openFd("$ASSET_DIR/$name").use { it.length }) }
    }

    /** The dictionary title in an archive's `index.json`. */
    suspend fun titleOf(asset: Asset): String? = withContext(Dispatchers.IO) {
        runCatching {
            ZipInputStream(context.assets.open("$ASSET_DIR/${asset.name}").buffered()).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == "index.json" }
                    ?.let { Json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject["title"]?.jsonPrimitive?.content }
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

        /** Shipped archives of which some version was installed before. */
        fun installedBefore(shipped: List<Asset>, installed: Set<String>): List<Asset> {
            val installedNames = installed.map { it.substringBeforeLast(':') }.toSet()
            return shipped.filter { it.name in installedNames }
        }

        /** A title without a trailing bracketed version, e.g. `JMdict [2026-09-27]` → `JMdict`, as updates change it. */
        fun baseTitle(title: String): String = title.replace(VERSION_SUFFIX, "")

        /**
         * Whether the installed dictionary titled [installed] is the bundled one titled [bundled] in another revision.
         * Older builds of English dictionaries name the language: `KANJIDIC (English)` for `KANJIDIC [2026-270]`.
         */
        fun sameTitle(installed: String, bundled: String): Boolean = ownKey(installed) == ownKey(bundled)

        private fun ownKey(title: String): String = dictionaryKey(title).removeSuffix(" (english)")

        private val VERSION_SUFFIX = Regex("""\s*\[[^\]]*]$""")

        private fun slot(name: String): String = name.substringBefore('-')
    }
}
