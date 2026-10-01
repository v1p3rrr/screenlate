package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Restores dictionaries whose files are gone while their registry entries remain. */
@Singleton
class DictionaryRepair @Inject constructor(
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
    private val imports: DictionaryImports,
) {
    /**
     * Queues bundled dictionaries with missing files for reinstallation from the APK (they keep their position and
     * enabled state). Returns the other dictionaries with missing files: those need the user to download or import
     * them again, including dictionaries an earlier version bundled and this APK no longer ships.
     */
    suspend fun repair(): List<DictionaryEntity> {
        val missing = repository.missingFiles()
        val bundledTitles = missing.filter { it.bundled }.map { it.title }.toSet()
        if (bundledTitles.isEmpty()) return missing
        val assets = bundled.all().associateWith { bundled.titleOf(it) }.filterValues { it in bundledTitles }
        if (assets.isNotEmpty()) {
            bundled.markForRepair(assets.keys)
            imports.installBundled()
        }
        return missing.filterNot { it.bundled && it.title in assets.values }
    }
}
