package com.vpr.screenlate.languages

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.ocr.model.LanguageModel
import com.vpr.screenlate.core.ocr.model.LanguageModels
import com.vpr.screenlate.core.ocr.model.OcrModelSpec
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.core.ocr.tesseract.TesseractOcrEngine
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.ModelInstaller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Catalog models go into the OCR model store. */
@Singleton
class OcrModelInstaller @Inject constructor(private val store: OcrModelStore) : ModelInstaller {
    override suspend fun install(entry: CatalogEntry, archive: File) {
        store.install(entry.modelSpec(), archive)
    }
}

/** A language reads with the models of its catalog entries. */
@Singleton
class CatalogLanguageModels @Inject constructor(private val catalog: DictionaryCatalog) : LanguageModels {
    override fun of(language: Language): List<LanguageModel> = languageModels(language.code, catalog.localCatalog().entries)
}

/**
 * [language]'s model entries in catalog order; a model several entries of the language name is listed once. A model
 * that others require (the Paddle detector) reads nothing alone and is left out.
 */
internal fun languageModels(language: String, entries: List<CatalogEntry>): List<LanguageModel> {
    val required = entries.flatMapTo(hashSetOf()) { it.requires }
    return entries.filter { it.isModel && it.sourceLanguage == language && it.model !in required }
        .distinctBy { it.model }
        .map { LanguageModel(requireNotNull(it.model), requireNotNull(it.engine), it.requires, it.recommended) }
}

/** The store's description of a catalog model; the catalog leaves out incomplete model entries. */
internal fun CatalogEntry.modelSpec(): OcrModelSpec =
    OcrModelSpec(
        id = requireNotNull(model),
        version = requireNotNull(version),
        engine = requireNotNull(engine),
        sha256 = requireNotNull(sha256),
        file = file,
    )

/**
 * Whether the model lists say under this model that it often misreads text on busy screens: every Tesseract model,
 * as Tesseract misread 26-74% of the characters of hard screens in every language compared.
 */
internal val CatalogEntry.misreadsBusyScreens: Boolean get() = engine == TesseractOcrEngine.ID

/** Whether [store] holds this model entry's version. */
internal fun CatalogEntry.isInstalledIn(store: OcrModelStore): Boolean {
    val id = model ?: return false
    return isModel && store.isInstalled(id, version ?: return false)
}

/**
 * Models of [language]'s catalog entries that no language of [others] lists too: what turning [language] off can
 * delete. A shared model, such as a text detector, stays while another turned-on language uses it.
 */
internal fun modelsOnlyFor(language: String, others: Collection<String>, entries: List<CatalogEntry>): Set<String> {
    val models = entries.filter { it.isModel }
    val kept = models.filter { it.sourceLanguage in others }.mapNotNullTo(hashSetOf()) { it.model }
    return models.filter { it.sourceLanguage == language }.mapNotNullTo(linkedSetOf()) { it.model } - kept
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OcrModelsModule {
    @Binds
    abstract fun bindInstaller(installer: OcrModelInstaller): ModelInstaller

    @Binds
    abstract fun bindLanguageModels(models: CatalogLanguageModels): LanguageModels
}
