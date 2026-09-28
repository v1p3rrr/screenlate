package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * Each archive is installed once, identified by name and size, so a dictionary the user deleted does not come
 * back, while a different archive shipped by a later app version is installed (replacing the same title).
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

    suspend fun pending(): List<Asset> = pending(all(), dataStore.data.first()[INSTALLED].orEmpty())

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

        /** [shipped] archives to install, given the `name:size` keys of the archives installed before. */
        fun pending(shipped: List<Asset>, installed: Set<String>): List<Asset> {
            val shippedNames = shipped.map { it.name }.toSet()
            val installedNames = installed.map { it.substringBeforeLast(':') }
            val replacedSlots = installedNames.filter { it !in shippedNames }.map(::slot).toSet()
            return shipped.filter { asset ->
                asset.key !in installed && (asset.name in installedNames || slot(asset.name) !in replacedSlots)
            }
        }

        private fun slot(name: String): String = name.substringBefore('-')
    }
}
