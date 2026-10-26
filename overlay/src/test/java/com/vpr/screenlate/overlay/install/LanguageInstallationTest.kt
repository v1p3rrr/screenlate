package com.vpr.screenlate.overlay.install

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
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
    ) = installationStatus(Language.ENGLISH, dictionaries, tasks, catalog, models, Language.JAPANESE, device, settings, imageSource)

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
