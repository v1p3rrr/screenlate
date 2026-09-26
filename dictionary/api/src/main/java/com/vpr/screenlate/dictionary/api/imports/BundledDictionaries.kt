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

    suspend fun pending(): List<Asset> = withContext(Dispatchers.IO) {
        val installed = dataStore.data.first()[INSTALLED].orEmpty()
        context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".zip") }
            .sorted()
            .map { name -> Asset(name, context.assets.openFd("$ASSET_DIR/$name").use { it.length }) }
            .filter { it.key !in installed }
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

    private companion object {
        const val ASSET_DIR = "dictionaries"
        val INSTALLED = stringSetPreferencesKey("bundled_dictionaries_installed")
    }
}
