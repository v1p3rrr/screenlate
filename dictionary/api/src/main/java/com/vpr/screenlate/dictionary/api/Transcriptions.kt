package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.TranscriptionRegions
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.PitchGroup
import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags

/**
 * Cleans up the phonetic transcriptions of Wiktionary dictionaries, which merge every edition and list dozens for a
 * common word, and adds the regions their tags name:
 * - a list in one string (`/a/, /b/`) is split into its transcriptions;
 * - transcriptions in an ASCII notation (SAMPA, X-SAMPA) or with quoted text in them are dropped;
 * - a phonetic `[…]` and a phonemic `/…/` form with the same symbols, syllable dots aside, become one, the phonemic
 *   one, with the regions of both.
 */
internal object Transcriptions {

    fun prepare(result: LookupResult, tags: Map<String, TranscriptionTags>, regionNames: Map<String, String>): LookupResult {
        val pitches = result.term.pitches
        if (pitches.none { it.transcriptions.isNotEmpty() }) return result
        val prepared = pitches.map { group ->
            if (group.transcriptions.isEmpty()) group else prepare(group, result.term.expression, tags[group.dictionary], regionNames)
        }
        return result.copy(term = result.term.copy(pitches = prepared))
    }

    fun prepare(group: PitchGroup, term: String, tags: TranscriptionTags?, regionNames: Map<String, String>): PitchGroup {
        val kept = mutableListOf<Item>()
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
                    Item(ipa, regions.toMutableList()).also {
                        kept += it
                        byCore[core] = it
                    }
                } else {
                    if (same.ipa.startsWith('[') && ipa.startsWith('/')) same.ipa = ipa
                    regions.filterTo(same.regions) { it !in same.regions }
                }
            }
        }
        val transcriptions = kept.map { it.ipa }
        if (transcriptions == group.transcriptions && kept.all { it.regions.isEmpty() }) return group
        return group.copy(
            transcriptions = transcriptions,
            transcriptionRegions = if (kept.any { it.regions.isNotEmpty() }) kept.map { it.regions.toList() } else emptyList(),
        )
    }

    /** The transcriptions of `/a/, /b/`; any other text is one transcription. */
    internal fun split(ipa: String): List<String> {
        if (", " !in ipa) return listOf(ipa)
        val parts = ipa.split(", ")
        return if (parts.all(::delimited)) parts else listOf(ipa)
    }

    /** The symbols of a transcription without its delimiters and syllable dots. */
    internal fun core(ipa: String): String = (if (delimited(ipa)) ipa.substring(1, ipa.length - 1) else ipa).replace(".", "")

    private fun delimited(ipa: String): Boolean =
        ipa.length > 2 && ((ipa.first() == '/' && ipa.last() == '/') || (ipa.first() == '[' && ipa.last() == ']'))

    private class Item(var ipa: String, val regions: MutableList<String>)

    /** Tags of transcriptions in an ASCII notation: SAMPA and X-SAMPA. */
    private val NOTATIONS = setOf("SAMPA", "XS", "X-SAMPA")
}
