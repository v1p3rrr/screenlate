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

    suspend fun pending(): List<Asset> {
        val installed = dataStore.data.first()[INSTALLED].orEmpty()
        return all().filter { it.key !in installed }
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

    private companion object {
        const val ASSET_DIR = "dictionaries"
        val INSTALLED = stringSetPreferencesKey("bundled_dictionaries_installed")
    }
}
