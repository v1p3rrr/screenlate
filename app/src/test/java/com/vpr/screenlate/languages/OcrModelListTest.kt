package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import java.util.UUID
import org.junit.Test

class OcrModelListTest {
    private fun model(source: String, model: String, engine: String, size: Long, recommended: Boolean = false, requires: List<String> = emptyList()) =
        CatalogEntry(
            id = "$source-$model", title = model, declaredCategory = CatalogCategory.OCR_MODEL, sourceLanguage = source,
            model = model, engine = engine, version = "1", sha256 = "ab", recommended = recommended, requires = requires,
            downloadUrl = "https://example.org/$model", downloadSize = size,
        )

    private val entries = listOf(
        model("en", "tesseract-fast-eng", "tesseract", 2),
        model("en", "paddle-detector", "paddle", 5),
        model("en", "paddle-latin", "paddle", 10, requires = listOf("paddle-detector")),
        model("de", "paddle-detector", "paddle", 5),
        model("de", "paddle-latin", "paddle", 10, recommended = true, requires = listOf("paddle-detector")),
        model("ru", "tesseract-fast-rus", "tesseract", 3, recommended = true),
        model("ru", "onnx-rus", "onnx", 4),
    )
    private val engines = setOf("tesseract", "paddle")
    private val mlKit = setOf(Language.JAPANESE, Language.ENGLISH, Language.GERMAN)

    private fun lists(
        installed: Map<String, Long> = emptyMap(),
        chosen: Map<Language, String> = emptyMap(),
        tasks: List<ImportTask> = emptyList(),
        turnedOn: List<Language> = listOf(Language.JAPANESE, Language.ENGLISH, Language.GERMAN, Language.RUSSIAN),
    ) = ocrModelLists(turnedOn, entries, installed, chosen, { it in mlKit }, engines, tasks).associateBy { it.language }

    @Test
    fun `ml kit comes first where it reads, then the usable catalog models without the detector`() {
        val lists = lists()
        assertThat(lists.getValue(Language.JAPANESE).options.map { it.id }).containsExactly("mlkit")
        assertThat(lists.getValue(Language.ENGLISH).options.map { it.id })
            .containsExactly("mlkit", "tesseract-fast-eng", "paddle-latin").inOrder()
        assertThat(lists.getValue(Language.RUSSIAN).options.map { it.id }).containsExactly("tesseract-fast-rus")

        // ML Kit is recommended where no catalog model is.
        assertThat(lists.getValue(Language.ENGLISH).options.filter { it.recommended }.map { it.id }).containsExactly("mlkit")
        assertThat(lists.getValue(Language.GERMAN).options.filter { it.recommended }.map { it.id }).containsExactly("paddle-latin")
        assertThat(lists.getValue(Language.GERMAN).active?.id).isEqualTo("mlkit")
        assertThat(lists.getValue(Language.RUSSIAN).active).isNull()
    }

    @Test
    fun `a missing model downloads with the models it requires`() {
        val latin = lists().getValue(Language.ENGLISH).options.single { it.id == "paddle-latin" }
        assertThat(latin.installed).isFalse()
        assertThat(latin.downloads.map { it.id }).containsExactly("en-paddle-latin", "en-paddle-detector").inOrder()
        assertThat(latin.bytes).isEqualTo(15)

        val withDetector = lists(installed = mapOf("paddle-detector" to 6)).getValue(Language.ENGLISH).options.single { it.id == "paddle-latin" }
        assertThat(withDetector.downloads.map { it.id }).containsExactly("en-paddle-latin")
        assertThat(withDetector.bytes).isEqualTo(10)

        val task = ImportTask(UUID.randomUUID(), "x", ImportTask.State.DOWNLOADING, 40, emptyList(), null, catalogEntry = "en-paddle-detector")
        assertThat(lists(tasks = listOf(task)).getValue(Language.ENGLISH).options.single { it.id == "paddle-latin" }.percent).isEqualTo(40)
        val done = task.copy(state = ImportTask.State.SUCCEEDED)
        assertThat(lists(tasks = listOf(done)).getValue(Language.ENGLISH).options.single { it.id == "paddle-latin" }.downloading).isFalse()
    }

    @Test
    fun `the active model cannot be deleted and a shared one goes for every language listing it`() {
        val installed = mapOf("paddle-detector" to 6L, "paddle-latin" to 11L, "tesseract-fast-eng" to 2L)

        // German reads with the Paddle model it chose, so nobody can delete it.
        val inUse = lists(installed, chosen = mapOf(Language.GERMAN to "paddle-latin")).getValue(Language.ENGLISH).options
            .single { it.id == "paddle-latin" }
        assertThat(inUse.installed).isTrue()
        assertThat(inUse.bytes).isEqualTo(11)
        assertThat(inUse.deletion).isEqualTo(ModelDeletion.InUse(listOf(Language.GERMAN)))

        // Without a choice German reads with ML Kit: deleting the model from English's list takes it from German too.
        val free = lists(installed)
        assertThat(free.getValue(Language.ENGLISH).options.single { it.id == "paddle-latin" }.deletion)
            .isEqualTo(ModelDeletion.Allowed(listOf(Language.GERMAN)))
        assertThat(free.getValue(Language.ENGLISH).options.single { it.id == "tesseract-fast-eng" }.deletion)
            .isEqualTo(ModelDeletion.Allowed(emptyList()))
        assertThat(free.getValue(Language.ENGLISH).options.first().deletion).isEqualTo(ModelDeletion.None)

        val chosen = lists(installed, chosen = mapOf(Language.ENGLISH to "tesseract-fast-eng", Language.GERMAN to "mlkit"))
        assertThat(chosen.getValue(Language.ENGLISH).active?.id).isEqualTo("tesseract-fast-eng")
        assertThat(chosen.getValue(Language.ENGLISH).options.single { it.id == "tesseract-fast-eng" }.deletion)
            .isEqualTo(ModelDeletion.InUse(listOf(Language.ENGLISH)))
    }

    @Test
    fun `the detector goes with the last model that requires it`() {
        val latin = listOf(
            model("en", "paddle-latin", "paddle", 10, requires = listOf("paddle-detector")),
            model("ru", "paddle-cyrillic", "paddle", 10, requires = listOf("paddle-detector")),
            model("en", "paddle-detector", "paddle", 5),
        )
        assertThat(modelsToDelete("paddle-latin", latin, listOf("paddle-latin", "paddle-detector")))
            .containsExactly("paddle-latin", "paddle-detector").inOrder()
        assertThat(modelsToDelete("paddle-latin", latin, listOf("paddle-latin", "paddle-cyrillic", "paddle-detector")))
            .containsExactly("paddle-latin")
        assertThat(modelsToDelete("tesseract-fast-eng", entries, listOf("tesseract-fast-eng", "paddle-detector")))
            .containsExactly("tesseract-fast-eng")
    }
}
