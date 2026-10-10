package com.vpr.screenlate.languages

import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity

/** A catalog entry on the download screen; an [installed] one stays ticked and is not downloaded again. */
data class SetupItem(val entry: CatalogEntry, val installed: Boolean)

/**
 * One category of the download screen. A [mandatory] category needs a ticked item; its only item is a preselected
 * radio button ([fixed]).
 */
data class SetupCategory(val category: CatalogCategory, val items: List<SetupItem>, val mandatory: Boolean) {
    val fixed: Boolean get() = mandatory && items.size == 1

    /** Installed and fixed items always; the others as the user set them ([choices]) or as the catalog recommends. */
    fun ticked(item: SetupItem, choices: Map<String, Boolean>): Boolean =
        item.installed || fixed || (choices[item.entry.id] ?: item.entry.recommended)
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

/** The gloss languages of [language]'s dictionaries in the catalog, for the slot the user fills; [taken] are left out. */
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
 * The categories of [language]'s download screen with the gloss languages [glosses]: dictionaries into one of them,
 * and those without a gloss language (frequency, transcription). Categories go in their declared order, a mandatory
 * one even when empty; within one, in the order of [glosses], the recommended first.
 */
internal fun setupCategories(
    catalog: Catalog,
    language: String,
    glosses: List<String>,
    installed: List<DictionaryEntity>,
): List<SetupCategory> {
    val entries = catalog.entries.filter { it.sourceLanguage == language && (it.targetLanguage == null || it.targetLanguage in glosses) }
    val mandatory = catalog.mandatory(language)
    return CatalogCategory.entries.mapNotNull { category ->
        val items = entries.filter { it.category == category }
            .sortedWith(
                compareBy<CatalogEntry> { entry -> entry.targetLanguage?.let(glosses::indexOf) ?: glosses.size }
                    .thenBy { !it.recommended },
            )
            .map { entry -> SetupItem(entry, installed.any(entry::matches)) }
        SetupCategory(category, items, category in mandatory).takeIf { items.isNotEmpty() || it.mandatory }
    }
}

/** Mandatory categories without a ticked item; Confirm stays disabled while there are any. */
internal fun missingChoices(categories: List<SetupCategory>, choices: Map<String, Boolean>): List<CatalogCategory> =
    categories.filter { category -> category.mandatory && category.items.none { category.ticked(it, choices) } }.map { it.category }

/** The ticked entries to download, by category and within one the smallest first, so the language works soonest. */
internal fun setupDownloads(categories: List<SetupCategory>, choices: Map<String, Boolean>): List<CatalogEntry> =
    categories.flatMap { category ->
        category.items.filter { !it.installed && category.ticked(it, choices) }.map { it.entry }.sortedBy { it.downloadBytes }
    }

/** Downloads come one at a time: the largest archive lies next to everything imported before it is unpacked. */
internal fun setupTotals(downloads: List<CatalogEntry>): SetupTotals {
    val installed = downloads.sumOf { it.installedBytes }
    return SetupTotals(
        downloadBytes = downloads.sumOf { it.downloadBytes },
        installedBytes = installed,
        neededBytes = installed + (downloads.maxOfOrNull { it.downloadBytes } ?: 0L),
    )
}
