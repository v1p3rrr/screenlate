package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.TranscriptionRegions
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.PitchGroup
import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags

/**
 * Cleans up the phonetic transcriptions of Wiktionary dictionaries, which merge every edition and list dozens for a
 * common word, and adds their tags and the regions the tags name:
 * - a list in one string (`/a/, /b/`, `//a/ [b]/`) is split into its transcriptions;
 * - transcriptions in an ASCII notation (SAMPA, X-SAMPA) or with quoted text in them are dropped;
 * - a phonetic `[…]` and a phonemic `/…/` form with the same symbols, syllable dots aside, become one, the phonemic
 *   one, with the tags and regions of both;
 * - those whose tags the language puts first (`LanguageSupport.firstTranscription`) go before the others.
 */
internal object Transcriptions {

    fun prepare(
        result: LookupResult,
        tags: Map<String, TranscriptionTags>,
        regionNames: Map<String, String>,
        first: (List<String>) -> Boolean = { false },
    ): LookupResult {
        val pitches = result.term.pitches
        if (pitches.none { it.transcriptions.isNotEmpty() }) return result
        val prepared = pitches.map { group ->
            if (group.transcriptions.isEmpty()) {
                group
            } else {
                prepare(group, result.term.expression, tags[group.dictionary], regionNames, first)
            }
        }
        return result.copy(term = result.term.copy(pitches = prepared))
    }

    fun prepare(
        group: PitchGroup,
        term: String,
        tags: TranscriptionTags?,
        regionNames: Map<String, String>,
        first: (List<String>) -> Boolean = { false },
    ): PitchGroup {
        val found = mutableListOf<Item>()
        val byCore = HashMap<String, Item>()
        for (whole in group.transcriptions) {
            val itemTags = tags?.of(term, whole).orEmpty()
            if (itemTags.any { it in NOTATIONS }) continue
            val regions = TranscriptionRegions.of(itemTags, regionNames)
            for (ipa in split(whole)) {
                if ('"' in ipa) continue
                val core = core(ipa)
                val same = byCore[core]
                if (same == null) {
                    Item(ipa, regions.toMutableList(), itemTags.toMutableList()).also {
                        found += it
                        byCore[core] = it
                    }
                } else {
                    if (same.ipa.startsWith('[') && ipa.startsWith('/')) same.ipa = ipa
                    regions.filterTo(same.regions) { it !in same.regions }
                    itemTags.filterTo(same.tags) { it !in same.tags }
                }
            }
        }
        val kept = found.sortedBy { !first(it.tags) }
        val transcriptions = kept.map { it.ipa }
        if (transcriptions == group.transcriptions && kept.all { it.regions.isEmpty() && it.tags.isEmpty() }) return group
        return group.copy(
            transcriptions = transcriptions,
            transcriptionRegions = if (kept.any { it.regions.isNotEmpty() }) kept.map { it.regions.toList() } else emptyList(),
            transcriptionTags = if (kept.any { it.tags.isNotEmpty() }) kept.map { it.tags.toList() } else emptyList(),
        )
    }

    /**
     * The transcriptions of `/a/, /b/`, and of `//a/ [b]/` or `//a//`, which the converter writes for one Wiktionary
     * template giving a phonemic and a phonetic form (6% of the Hebrew transcriptions); any other text is one
     * transcription.
     */
    internal fun split(ipa: String): List<String> {
        if (ipa.startsWith("//") && ipa.endsWith("/")) {
            val inner = ipa.substring(1, ipa.length - 1)
            val parts = inner.split(" ")
            if (parts.size > 1 && parts.all(::delimited)) return parts
            if (delimited(inner)) return listOf(inner)
        }
        if (", " !in ipa) return listOf(ipa)
        val parts = ipa.split(", ")
        return if (parts.all(::delimited)) parts else listOf(ipa)
    }

    /** The symbols of a transcription without its delimiters and syllable dots. */
    internal fun core(ipa: String): String = (if (delimited(ipa)) ipa.substring(1, ipa.length - 1) else ipa).replace(".", "")

    private fun delimited(ipa: String): Boolean =
        ipa.length > 2 && ((ipa.first() == '/' && ipa.last() == '/') || (ipa.first() == '[' && ipa.last() == ']'))

    private class Item(var ipa: String, val regions: MutableList<String>, val tags: MutableList<String>)

    /** Tags of transcriptions in an ASCII notation: SAMPA and X-SAMPA. */
    private val NOTATIONS = setOf("SAMPA", "XS", "X-SAMPA")
}
