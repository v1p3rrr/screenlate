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
     * them again.
     */
    suspend fun repair(): List<DictionaryEntity> {
        val missing = repository.missingFiles()
        val bundledTitles = missing.filter { it.bundled }.map { it.title }.toSet()
        if (bundledTitles.isNotEmpty()) {
            val assets = bundled.all().filter { bundled.titleOf(it) in bundledTitles }
            if (assets.isNotEmpty()) {
                bundled.forgetAll(assets)
                imports.installBundled()
            }
        }
        return missing.filterNot { it.bundled }
    }
}
