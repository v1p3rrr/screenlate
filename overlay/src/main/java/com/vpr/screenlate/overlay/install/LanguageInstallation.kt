package com.vpr.screenlate.overlay.install

import android.content.Context
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.hasCode
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.model.DeviceOcr
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.isFor
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.TextSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Readiness and remaining installations for one language, including optional downloads and failed tasks. */
data class InstallationStatus(
    val language: Language,
    val missing: Set<CatalogCategory>,
    val deviceReady: Boolean,
    val requiresDevice: Boolean,
    val tasks: List<ImportTask>,
    val failures: Int,
) {
    val dictionaryReady: Boolean get() = missing.none { it != CatalogCategory.OCR_MODEL }
    val scanReady: Boolean get() = missing.isEmpty() && (!requiresDevice || deviceReady)

    /** Only an on-device model keeps a scan from starting, with nothing downloading or failed. */
    val needsDeviceModel: Boolean get() = missing.isEmpty() && requiresDevice && !deviceReady && failures == 0 && tasks.isEmpty()

    fun message(context: Context, scanning: Boolean = false): String = context.getString(
        when {
            (if (scanning) scanReady else dictionaryReady) -> R.string.installation_ready
            failures > 0 -> R.string.installation_failed
            tasks.isNotEmpty() -> R.string.installation_running
            scanning && needsDeviceModel -> R.string.overlay_error_no_device_ocr
            else -> R.string.installation_missing
        },
    )
}

/** Shared by Home, Search and the overlay; reading readiness never starts downloads or recognition. */
@Singleton
class LanguageInstallation @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    private val catalog: DictionaryCatalog,
    private val bundled: BundledDictionaries,
    private val models: OcrModelStore,
    private val device: DeviceOcr,
    private val settings: OverlaySettingsRepository,
) {
    fun observe(language: Language): Flow<InstallationStatus> = combine(
        repository.dictionaries.map { all ->
            val missing = repository.missingFiles().mapTo(hashSetOf()) { it.id }
            all.filterNot { it.id in missing }
        },
        imports.tasks,
        flow { emit(catalog.localCatalog()) }.flowOn(Dispatchers.IO),
        models.models,
        settings.settings,
    ) { dictionaries, tasks, catalog, models, settings ->
        installationStatus(language, dictionaries, tasks, catalog, models, bundled.language, device.reads(language), settings)
    }.distinctUntilChanged()

    suspend fun current(language: Language): InstallationStatus = observe(language).first()
}

internal fun installationStatus(
    language: Language,
    dictionaries: List<DictionaryEntity>,
    tasks: List<ImportTask>,
    catalog: Catalog,
    models: List<InstalledOcrModel>,
    bundledLanguage: Language,
    deviceReady: Boolean,
    settings: OverlaySettings,
): InstallationStatus {
    val own = dictionaries.filter { it.isFor(language) }
    val enabled = own.filter { it.enabled }
    val entries = catalog.entries.filter { it.isFor(language) }
    val missing = catalog.mandatory(language).filterTo(linkedSetOf()) { category ->
        when (category) {
            CatalogCategory.MAIN -> enabled.none { it.termCount > 0 }
            // Any version counts: an installed model keeps working until a newer one replaces it. A model that others
            // require (a text detector shared with other languages) reads nothing alone.
            CatalogCategory.OCR_MODEL -> {
                val required = entries.flatMapTo(hashSetOf()) { it.requires }
                entries.filter { it.isModel && it.model !in required }.none { entry -> models.any { it.id == entry.model } } &&
                    settings.textSource != TextSource.APP_TEXT_ONLY
            }
            else -> entries.filter { it.category == category }.none { entry -> enabled.any(entry::matches) }
        }
    }
    val names = entries.mapTo(hashSetOf()) { it.title }.apply { own.forEach { add(it.title) } }
    val relevant = tasks.filter { task ->
        if (task.language != null) language.hasCode(task.language)
        else task.name in names || task.titles.any { it in names } || (language == bundledLanguage && (task.source == SOURCE_BUNDLED || task.paused))
    }
    // A retry supersedes the older failed task of the same entry. Completed failures otherwise remain visible.
    val latest = relevant.groupBy { it.catalogEntry ?: it.name }.values.map { it.last() }
    return InstallationStatus(
        language, missing, deviceReady,
        settings.textSource != TextSource.APP_TEXT_ONLY && settings.ocrEngines == OcrEngines.DEVICE,
        relevant.filterNot { it.finished }, latest.count { it.state == ImportTask.State.FAILED },
    )
}
