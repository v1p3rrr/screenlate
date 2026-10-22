package com.vpr.screenlate.languages

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity

/**
 * A catalog entry on the download screen; an [installed] one stays ticked and is not downloaded again. [requires] are
 * the models it reads only together with (the Paddle detector), downloaded with it when missing and not listed. An
 * [ownEdition] is a dictionary of the language's own Wiktionary edition listed although the language is not among the
 * chosen gloss languages; it is not ticked by default. A [fallback] item is a dictionary of the language its lookups
 * fall back to (Czech for Slovak, `LanguageSupport.fallbackLanguage`): not ticked by default, and it does not count
 * for a mandatory category.
 */
data class SetupItem(
    val entry: CatalogEntry,
    val installed: Boolean,
    val requires: List<SetupItem> = emptyList(),
    val ownEdition: Boolean = false,
    val fallback: Boolean = false,
) {
    /** What ticking it downloads: the entry and the missing models it requires. */
    val downloadBytes: Long get() = entry.downloadBytes + requires.filterNot { it.installed }.sumOf { it.entry.downloadBytes }
}

/**
 * One category of the download screen. A [mandatory] category needs a ticked item; its only item is a preselected
 * radio button ([fixed]). [builtIn] is the on-device model category of a language ML Kit reads: ML Kit is listed first
 * and needs no download, so nothing else is ticked by default and nothing is missing.
 */
data class SetupCategory(
    val category: CatalogCategory,
    val items: List<SetupItem>,
    val mandatory: Boolean,
    val builtIn: Boolean = false,
) {
    val fixed: Boolean get() = mandatory && !builtIn && items.count { !it.fallback } == 1

    /**
     * Installed and fixed items always; the others as the user set them ([choices]) or as the catalog recommends,
     * except beside ML Kit and for an [SetupItem.ownEdition] or a [SetupItem.fallback].
     */
    fun ticked(item: SetupItem, choices: Map<String, Boolean>): Boolean =
        item.installed || (fixed && !item.fallback) ||
            (choices[item.entry.id] ?: (item.entry.recommended && !builtIn && !item.ownEdition && !item.fallback))
}

/** Archive bytes to download, bytes after import, and the space the downloads need on the way. */
data class SetupTotals(val downloadBytes: Long, val installedBytes: Long, val neededBytes: Long)

/** Above this total the download screen asks before downloading over mobile data. */
const val MOBILE_DATA_ASK_BYTES = 50_000_000L

/**
 * The gloss languages offered first for [language]: the interface language and English. The interface language's
 * slot is null, for the user to fill, when it is English, [language] itself, or not among the [available] gloss
 * languages of [language]'s dictionaries.
 */
internal fun glossSlots(language: String, interfaceLanguage: String, available: Collection<String>): List<String?> =
    listOf(interfaceLanguage.takeUnless { it == "en" || it == language || it !in available }, "en")

/**
 * The gloss languages of the dictionaries of [language] (a [Language.dictionaryCode]) in the catalog, for the slot the
 * user fills; [taken] are left out.
 */
internal fun glossChoices(
    entries: List<CatalogEntry>,
    language: String,
    taken: Collection<String>,
    order: Comparator<String?>,
): List<String> = entries.asSequence()
    .filter { it.sourceLanguage == language }
    .mapNotNull { it.targetLanguage }
    .distinct()
    .filterNot { it in taken }
    .sortedWith(order)
    .toList()

/**
 * The categories of [language]'s download screen with the gloss languages [glosses]: dictionaries into one of them or
 * into [language] itself (its own Wiktionary edition, after the others), and those without a gloss language
 * (frequency, transcription, the entry's own recognition models). Categories go in their declared order, a mandatory one even when
 * empty; within one, in the order of [glosses], the recommended first. A model counts
 * as installed when [modelInstalled] says so; a model others require is not listed but goes with them
 * ([SetupItem.requires]). Where [mlKitReads] the language, the model category is [SetupCategory.builtIn]. The main
 * dictionaries of the language its lookups fall back to, into [glosses], follow the language's own as
 * [SetupItem.fallback] items.
 */
internal fun setupCategories(
    catalog: Catalog,
    language: Language,
    glosses: List<String>,
    installed: List<DictionaryEntity>,
    mlKitReads: Boolean = false,
    modelInstalled: (CatalogEntry) -> Boolean = { false },
): List<SetupCategory> {
    val code = language.dictionaryCode
    val fallbackCode = language.support.fallbackLanguage?.dictionaryCode
    fun fallback(entry: CatalogEntry) = !entry.isFor(language) && entry.sourceLanguage == fallbackCode
    val entries = catalog.entries.filter {
        it.isFor(language) && (it.targetLanguage == null || it.targetLanguage in glosses || it.targetLanguage == code) ||
            (fallback(it) && it.category == CatalogCategory.MAIN && it.isReadBy(language) && it.targetLanguage in glosses)
    }
    val mandatory = catalog.mandatory(language)
    val required = catalog.entries.flatMapTo(hashSetOf()) { it.requires }
    fun requiredItems(entry: CatalogEntry) = entry.requires.mapNotNull { id ->
        val model = entries.firstOrNull { it.isModel && it.model == id } ?: catalog.entries.firstOrNull { it.isModel && it.model == id }
        model?.let { SetupItem(it, modelInstalled(it)) }
    }
    return CatalogCategory.entries.mapNotNull { category ->
        val items = entries.filter { it.category == category && !(it.isModel && it.model in required) }
            .sortedWith(
                compareBy<CatalogEntry> { fallback(it) }
                    .thenBy { entry -> entry.targetLanguage?.let(glosses::indexOf)?.takeIf { it >= 0 } ?: glosses.size }
                    .thenBy { !it.recommended },
            )
            .map { entry ->
                if (entry.isModel) {
                    SetupItem(entry, modelInstalled(entry), requiredItems(entry))
                } else {
                    SetupItem(
                        entry,
                        installed.any(entry::matches),
                        ownEdition = entry.targetLanguage == code && code !in glosses,
                        fallback = fallback(entry),
                    )
                }
            }
        SetupCategory(category, items, category in mandatory, builtIn = mlKitReads && category == CatalogCategory.OCR_MODEL)
            .takeIf { items.isNotEmpty() || it.mandatory }
    }
}

/**
 * The ticked on-device models that read the language themselves, each with a "main" switch; a model others require
 * (the Paddle detector) is left out.
 */
internal fun mainModelCandidates(categories: List<SetupCategory>, choices: Map<String, Boolean>, required: Set<String>): List<CatalogEntry> =
    categories.filter { it.category == CatalogCategory.OCR_MODEL }.flatMap { category ->
        category.items.filter { category.ticked(it, choices) && it.entry.model != null && it.entry.model !in required }.map { it.entry }
    }.distinctBy { it.model }

/**
 * The model of [candidates] that becomes the language's on-device model: the user's [pick] (a model id, or null for
 * none) while it is still a candidate. Else, where ML Kit reads the language, a candidate the user ticked ([ticked],
 * entry ids; the recommended first), or none, which leaves ML Kit; a model ticked only because it is installed does not
 * count. Without ML Kit, the recommended candidate, else the first: one of them is always main.
 */
internal fun mainModel(candidates: List<CatalogEntry>, pick: MainModelPick?, mlKitReads: Boolean, ticked: Set<String> = emptySet()): String? {
    val ids = candidates.mapNotNull { it.model }
    if (pick != null && (pick.model in ids || (pick.model == null && mlKitReads))) return pick.model
    if (mlKitReads) {
        val chosen = candidates.filter { it.id in ticked }
        return (chosen.firstOrNull { it.recommended } ?: chosen.firstOrNull())?.model
    }
    return candidates.firstOrNull { it.recommended }?.model ?: ids.firstOrNull()
}

/** The user's main model on the download screen: a model id, or null for none. */
data class MainModelPick(val model: String?)

/** Mandatory categories without a ticked item; Confirm stays disabled while there are any. */
internal fun missingChoices(categories: List<SetupCategory>, choices: Map<String, Boolean>): List<CatalogCategory> =
    categories.filter { category ->
        category.mandatory && !category.builtIn && category.items.none { !it.fallback && category.ticked(it, choices) }
    }
        .map { it.category }

/**
 * The ticked entries to download with the missing models they require, by category and within one the smallest
 * first, so the language works soonest; a model shared by several is downloaded once.
 */
internal fun setupDownloads(categories: List<SetupCategory>, choices: Map<String, Boolean>): List<CatalogEntry> =
    categories.flatMap { category ->
        category.items.filter { category.ticked(it, choices) }
            .flatMap { item -> (listOf(item) + item.requires).filterNot { it.installed }.map { it.entry } }
            .distinctBy { it.model ?: it.id }
            .sortedBy { it.downloadBytes }
    }

/** Installed copies of ticked entries to enable on confirmation; no archive is downloaded for them. */
internal fun setupInstalled(
    categories: List<SetupCategory>,
    choices: Map<String, Boolean>,
    installed: List<DictionaryEntity>,
): Set<Long> {
    val selected = categories.flatMap { category -> category.items.filter { it.installed && category.ticked(it, choices) } }
        .filterNot { it.entry.isModel }
    return installed.filter { dictionary -> selected.any { it.entry.matches(dictionary) } }.mapTo(linkedSetOf()) { it.id }
}

/**
 * Downloads come one at a time: the largest archive lies next to everything imported before it is unpacked. Archives
 * the app carries count for space, not for the download.
 */
internal fun setupTotals(downloads: List<CatalogEntry>): SetupTotals {
    val installed = downloads.sumOf { it.installedBytes }
    return SetupTotals(
        downloadBytes = downloads.filterNot { it.isBundled }.sumOf { it.downloadBytes },
        installedBytes = installed,
        neededBytes = installed + (downloads.maxOfOrNull { it.downloadBytes } ?: 0L),
    )
}
