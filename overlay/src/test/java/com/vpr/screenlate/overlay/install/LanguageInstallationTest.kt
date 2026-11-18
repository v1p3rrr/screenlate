package com.vpr.screenlate.overlay.install

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.dictionary.api.catalog.BUILD_FORMS
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.TextSource
import java.io.File
import java.util.UUID
import org.junit.Test

class LanguageInstallationTest {
    private fun dictionary(title: String = "English", language: String? = "en", enabled: Boolean = true) = DictionaryEntity(
        1, title, "1", DictionaryKind.TERM, language, "en", null, enabled, 0, title,
        1, 0, 0, 0, 0, false, null, null, null, null, null, null, false, 0,
    )

    private fun entry(id: String, category: CatalogCategory = CatalogCategory.MAIN) = CatalogEntry(
        id, id, installedTitle = id, kind = DictionaryKind.TERM, declaredCategory = category,
        sourceLanguage = "en", downloadUrl = "https://example.org/$id.zip",
    )

    private fun task(language: String? = "en", state: ImportTask.State = ImportTask.State.IMPORTING, entry: String? = "main") =
        ImportTask(UUID.randomUUID(), "changed archive name", state, 25, emptyList(), null, language = language, catalogEntry = entry)

    private fun status(
        dictionaries: List<DictionaryEntity> = emptyList(), tasks: List<ImportTask> = emptyList(),
        catalog: Catalog = Catalog(listOf(entry("English"))), models: List<InstalledOcrModel> = emptyList(),
        device: Boolean = true, settings: OverlaySettings = OverlaySettings(), imageSource: Boolean = false,
        offlineMode: Boolean = false,
    ) = installationStatus(Language.ENGLISH, dictionaries, tasks, catalog, models, Language.JAPANESE, device, settings, imageSource, offlineMode)

    @Test
    fun photoCannotUseAppTextOnlyToBypassItsRecognitionModelButCachedLookupStillWorks() {
        val settings = OverlaySettings(textSource = TextSource.APP_TEXT_ONLY, ocrEngines = OcrEngines.DEVICE)
        assertThat(status(listOf(dictionary()), device = false, settings = settings).scanReady).isTrue()
        val image = status(listOf(dictionary()), device = false, settings = settings, imageSource = true)
        assertThat(image.scanReady).isFalse()
        assertThat(image.needsDeviceModel).isTrue()
        assertThat(image.dictionaryReady).isTrue()
        assertThat(status(listOf(dictionary()), device = false,
            settings = settings.copy(ocrEngines = OcrEngines.CLOUD), imageSource = true).scanReady).isTrue()
    }

    @Test
    fun `a new language remains blocked while other languages are installed`() {
        val installing = task()
        val result = status(listOf(dictionary(language = "ja")), listOf(installing, task("ja")))
        assertThat(result.dictionaryReady).isFalse()
        assertThat(result.scanReady).isFalse()
        assertThat(result.tasks).containsExactly(installing)
    }

    @Test
    fun `required forms block scanning until installed and enabled`() {
        val catalog = Catalog(listOf(entry("Forms", CatalogCategory.FORMS)), mapOf("en" to setOf(CatalogCategory.MAIN, CatalogCategory.FORMS)))
        assertThat(status(listOf(dictionary()), catalog = catalog).scanReady).isFalse()
        assertThat(status(listOf(dictionary(), dictionary("Forms", enabled = false)), catalog = catalog).scanReady).isFalse()
        assertThat(status(listOf(dictionary(), dictionary("Forms")), catalog = catalog).scanReady).isTrue()
    }

    @Test
    fun `the whole dictionary the forms are cut from counts as the forms`() {
        val index = "https://example.org/en-en/index.json"
        val forms = entry("wty-en-en (forms)", CatalogCategory.FORMS).copy(indexUrl = index, build = BUILD_FORMS)
        val catalog = Catalog(listOf(entry("English"), forms), mapOf("en" to setOf(CatalogCategory.MAIN, CatalogCategory.FORMS)))
        val whole = dictionary("wty-en-en").copy(indexUrl = index)
        assertThat(status(listOf(dictionary()), catalog = catalog).missing).containsExactly(CatalogCategory.FORMS)
        assertThat(status(listOf(dictionary(), whole), catalog = catalog).missing).isEmpty()
        assertThat(status(listOf(dictionary(), whole.copy(enabled = false)), catalog = catalog).missing)
            .containsExactly(CatalogCategory.FORMS)
    }

    @Test
    fun `optional downloads and failures stay visible after lookups become ready`() {
        val remaining = task(entry = "ipa")
        val result = status(listOf(dictionary()), listOf(remaining, task(state = ImportTask.State.FAILED, entry = "frequency")))
        assertThat(result.scanReady).isTrue()
        assertThat(result.tasks).containsExactly(remaining)
        assertThat(result.failures).isEqualTo(1)
    }

    @Test
    fun `a paused legacy bundled install belongs only to the bundled language`() {
        val paused = task(language = null, state = ImportTask.State.FAILED, entry = null).copy(paused = true)
        assertThat(status(tasks = listOf(paused)).failures).isEqualTo(0)
        assertThat(installationStatus(Language.JAPANESE, emptyList(), listOf(paused), Catalog(emptyList()), emptyList(),
            Language.JAPANESE, true, OverlaySettings()).failures).isEqualTo(1)
    }

    @Test
    fun `a newer retry supersedes its old failure`() {
        assertThat(status(tasks = listOf(task(state = ImportTask.State.FAILED), task())).failures).isEqualTo(0)
    }

    @Test
    fun `device-only scanning waits for recognition but cloud and app text can be used`() {
        val ready = listOf(dictionary())
        assertThat(status(ready, device = false).scanReady).isTrue()
        assertThat(status(ready, device = false, settings = OverlaySettings(ocrEngines = OcrEngines.DEVICE)).scanReady).isFalse()
        assertThat(status(ready, device = false, settings = OverlaySettings(ocrEngines = OcrEngines.DEVICE, textSource = TextSource.APP_TEXT_ONLY)).scanReady).isTrue()
    }

    @Test
    fun `only a missing on-device model points the scan message to the models`() {
        val device = OverlaySettings(ocrEngines = OcrEngines.DEVICE)
        assertThat(status(listOf(dictionary()), device = false, settings = device).needsDeviceModel).isTrue()
        assertThat(status(listOf(dictionary()), device = true, settings = device).needsDeviceModel).isFalse()
        assertThat(status(listOf(dictionary()), device = false).needsDeviceModel).isFalse()
        // A download in progress may bring the model; the message points to the queue.
        assertThat(status(listOf(dictionary()), listOf(task()), device = false, settings = device).needsDeviceModel).isFalse()
        val mandatory = Catalog(listOf(entry("English")), mapOf("en" to setOf(CatalogCategory.MAIN)))
        assertThat(status(emptyList(), catalog = mandatory, device = false, settings = device).needsDeviceModel).isFalse()
    }

    @Test
    fun `a mandatory OCR model blocks scanning but not typed lookup`() {
        val model = entry("OCR", CatalogCategory.OCR_MODEL).copy(model = "latin", version = "2", engine = "test", sha256 = "hash")
        val catalog = Catalog(listOf(model), mapOf("en" to setOf(CatalogCategory.MAIN, CatalogCategory.OCR_MODEL)))
        val result = status(listOf(dictionary()), catalog = catalog)
        assertThat(result.dictionaryReady).isTrue()
        assertThat(result.scanReady).isFalse()
        // An older version keeps working until the newer one is installed.
        assertThat(status(listOf(dictionary()), catalog = catalog, models = listOf(InstalledOcrModel("latin", "1", "test", File("old")))).scanReady).isTrue()
        assertThat(status(listOf(dictionary()), catalog = catalog, models = listOf(InstalledOcrModel("latin", "2", "test", File("ready")))).scanReady).isTrue()
        assertThat(status(listOf(dictionary()), catalog = catalog, models = listOf(InstalledOcrModel("other", "2", "test", File("other")))).scanReady).isFalse()
    }

    @Test
    fun `forced offline mode requires an on-device model even when cloud recognition is enabled`() {
        val ready = listOf(dictionary())
        val both = status(ready, device = false, settings = OverlaySettings(ocrEngines = OcrEngines.BOTH), offlineMode = true)
        assertThat(both.scanReady).isFalse()
        assertThat(both.needsDeviceModel).isTrue()
        assertThat(both.requiresDevice).isTrue()
        assertThat(both.offlineMode).isTrue()
        assertThat(both.messageRes(scanning = true)).isEqualTo(R.string.overlay_error_no_device_ocr_offline)

        val onlineDeviceOnly = status(ready, device = false, settings = OverlaySettings(ocrEngines = OcrEngines.DEVICE), offlineMode = false)
        assertThat(onlineDeviceOnly.messageRes(scanning = true)).isEqualTo(R.string.overlay_error_no_device_ocr)

        val cloud = status(ready, device = false, settings = OverlaySettings(ocrEngines = OcrEngines.CLOUD), offlineMode = true)
        assertThat(cloud.scanReady).isFalse()
        assertThat(cloud.needsDeviceModel).isTrue()

        val installed = status(ready, device = true, settings = OverlaySettings(ocrEngines = OcrEngines.BOTH), offlineMode = true)
        assertThat(installed.scanReady).isTrue()
        assertThat(installed.needsDeviceModel).isFalse()
        assertThat(installed.messageRes(scanning = true)).isEqualTo(R.string.installation_ready)
    }

    @Test
    fun `forced offline mode allows app-text-only screen scanning without a model but still blocks images`() {
        val ready = listOf(dictionary())
        val settings = OverlaySettings(textSource = TextSource.APP_TEXT_ONLY, ocrEngines = OcrEngines.BOTH)
        val screen = status(ready, device = false, settings = settings, offlineMode = true)
        assertThat(screen.scanReady).isTrue()
        assertThat(screen.needsDeviceModel).isFalse()

        val image = status(ready, device = false, settings = settings, imageSource = true, offlineMode = true)
        assertThat(image.scanReady).isFalse()
        assertThat(image.needsDeviceModel).isTrue()
        assertThat(image.offlineMode).isTrue()
    }

    @Test
    fun `a detector installed for another language is not the language's model`() {
        val recognizer = entry("Paddle", CatalogCategory.OCR_MODEL)
            .copy(model = "paddle-latin", version = "1", engine = "paddle", sha256 = "hash", requires = listOf("paddle-detector"))
        val detector = entry("Detector", CatalogCategory.OCR_MODEL).copy(model = "paddle-detector", version = "1", engine = "paddle", sha256 = "hash")
        val catalog = Catalog(listOf(recognizer, detector), mapOf("en" to setOf(CatalogCategory.MAIN, CatalogCategory.OCR_MODEL)))
        val detectorOnly = listOf(InstalledOcrModel("paddle-detector", "1", "paddle", File("detector")))
        assertThat(status(listOf(dictionary()), catalog = catalog, models = detectorOnly).scanReady).isFalse()
        val both = detectorOnly + InstalledOcrModel("paddle-latin", "1", "paddle", File("latin"))
        assertThat(status(listOf(dictionary()), catalog = catalog, models = both).scanReady).isTrue()
    }
}
