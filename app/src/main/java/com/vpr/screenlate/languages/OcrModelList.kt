package com.vpr.screenlate.languages

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.model.DeviceModel
import com.vpr.screenlate.core.ocr.model.OcrModelChoices
import com.vpr.screenlate.core.ocr.model.selectDeviceModel
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask

/** What the Delete button of a model does. */
sealed interface ModelDeletion {
    /** Nothing to delete: built in or not downloaded. */
    data object None : ModelDeletion

    /** The model reads [languages] and cannot be deleted, only replaced by another one in each of them. */
    data class InUse(val languages: List<Language>) : ModelDeletion

    /** The model can be deleted; the other turned-on languages [alsoFor] list it too and lose it. */
    data class Allowed(val alsoFor: List<Language>) : ModelDeletion
}

/**
 * One model in a language's list.
 *
 * @property id the model's id, or [OcrModelChoices.ML_KIT].
 * @property entry the model's catalog entry; null for ML Kit.
 * @property downloads what downloading the model fetches: its entry and the models it requires that are missing.
 * @property bytes the space the model takes when [installed], else what [downloads] weigh; 0 for ML Kit.
 * @property percent the progress of a download in progress, -1 while it is unknown; null without one.
 */
data class OcrModelOption(
    val id: String,
    val entry: CatalogEntry?,
    val installed: Boolean,
    val downloads: List<CatalogEntry>,
    val bytes: Long,
    val recommended: Boolean,
    val active: Boolean,
    val percent: Int?,
    val deletion: ModelDeletion,
) {
    val downloading: Boolean get() = percent != null
}

/** A turned-on language's models; [active] is the one it reads with, null when none can. */
data class LanguageModelList(val language: Language, val options: List<OcrModelOption>) {
    val active: OcrModelOption? get() = options.firstOrNull { it.active }
}

/**
 * The model lists of the [turnedOn] languages: ML Kit where [mlKitReads] the language (recommended when no catalog
 * model is), then the language's catalog models whose engine is in [engines]. [installed] gives each installed model's
 * size by id. A downloaded model that a language reads with cannot be deleted; one that other turned-on languages only
 * list goes for them too.
 */
internal fun ocrModelLists(
    turnedOn: List<Language>,
    entries: List<CatalogEntry>,
    installed: Map<String, Long>,
    chosen: Map<Language, String>,
    mlKitReads: (Language) -> Boolean,
    engines: Set<String>,
    tasks: List<ImportTask>,
): List<LanguageModelList> {
    val listed = turnedOn.associateWith { language -> languageModels(language.code, entries).filter { it.engine in engines } }
    val active = turnedOn.associateWith { language ->
        selectDeviceModel(chosen[language], listed.getValue(language), mlKitReads(language)) { model ->
            model.id in installed && model.requires.all { it in installed }
        }
    }
    fun users(id: String) = turnedOn.filter { active[it]?.id == id }
    return turnedOn.map { language ->
        val own = listed.getValue(language)
        val mlKit = if (mlKitReads(language)) {
            listOf(
                OcrModelOption(
                    id = OcrModelChoices.ML_KIT,
                    entry = null,
                    installed = true,
                    downloads = emptyList(),
                    bytes = 0,
                    recommended = own.none { it.recommended },
                    active = active[language] == DeviceModel.MlKit,
                    percent = null,
                    deletion = ModelDeletion.None,
                ),
            )
        } else {
            emptyList()
        }
        val catalog = own.mapNotNull { model ->
            val entry = modelEntry(model.id, language.code, entries) ?: return@mapNotNull null
            val missing = (listOf(model.id) + model.requires).filter { it !in installed }
            val downloads = missing.mapNotNull { modelEntry(it, language.code, entries) }
            val ready = missing.isEmpty()
            val task = tasks.lastOrNull { task -> !task.finished && downloads.any { it.id == task.catalogEntry } }
            val users = users(model.id)
            OcrModelOption(
                id = model.id,
                entry = entry,
                installed = ready,
                downloads = downloads,
                bytes = if (ready) installed.getValue(model.id) else downloads.sumOf { it.downloadBytes },
                recommended = model.recommended,
                active = active[language]?.id == model.id,
                percent = task?.let { it.percent ?: -1 },
                deletion = when {
                    model.id !in installed -> ModelDeletion.None
                    users.isNotEmpty() -> ModelDeletion.InUse(users)
                    else -> ModelDeletion.Allowed(turnedOn.filter { other -> other != language && listed.getValue(other).any { it.id == model.id } })
                },
            )
        }
        LanguageModelList(language, mlKit + catalog)
    }
}

/** The entry of model [id], [language]'s own when it has one: a shared model has an entry per language. */
private fun modelEntry(id: String, language: String, entries: List<CatalogEntry>): CatalogEntry? {
    val models = entries.filter { it.isModel && it.model == id }
    return models.firstOrNull { it.sourceLanguage == language } ?: models.firstOrNull()
}

/**
 * What deleting model [id] removes: the model and the models it requires that no other of the [installed] ones
 * requires, such as the Paddle detector with the last Paddle recognizer.
 */
internal fun modelsToDelete(id: String, entries: List<CatalogEntry>, installed: Collection<String>): List<String> {
    val requires = entries.filter { it.isModel }.groupBy { requireNotNull(it.model) }
        .mapValues { (_, own) -> own.flatMapTo(linkedSetOf()) { it.requires } }
    val still = installed.filter { it != id }.flatMapTo(hashSetOf()) { requires[it].orEmpty() }
    return listOf(id) + requires[id].orEmpty().filter { it in installed && it !in still }
}
