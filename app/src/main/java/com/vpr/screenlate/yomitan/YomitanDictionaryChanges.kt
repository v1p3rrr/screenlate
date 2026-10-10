package com.vpr.screenlate.yomitan

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionaries.reordered
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import com.vpr.screenlate.dictionary.api.registry.isFor

internal data class YomitanDictionaryChanges(
    val ordered: List<DictionaryEntity>,
    val switches: Map<Long, Boolean>,
    val missing: List<String>,
    val sort: DictionaryEntity?,
)

/** Applies the profile's dictionary order within the language's existing slots; other languages stay unchanged. */
internal fun dictionaryChanges(
    profile: YomitanSettings.Profile,
    language: Language,
    installed: List<DictionaryEntity>,
): YomitanDictionaryChanges {
    val own = installed.filter { it.isFor(language) }
    fun match(name: String, candidates: List<DictionaryEntity>): DictionaryEntity? =
        candidates.firstOrNull { it.title == name }
            ?: candidates.firstOrNull { dictionaryKey(it.title) == dictionaryKey(name) }
    val matched = profile.dictionaries.mapNotNull { preference ->
        match(preference.name, own)?.let { it to preference }
    }.distinctBy { it.first.id }
    val ordered = matched.map { it.first } + own.filter { dictionary -> matched.none { it.first.id == dictionary.id } }
    return YomitanDictionaryChanges(
        ordered = reordered(installed, ordered),
        switches = matched.associate { (dictionary, preference) -> dictionary.id to preference.enabled },
        missing = profile.dictionaries.map { it.name }.filter { match(it, installed) == null },
        sort = profile.sortFrequencyDictionary?.let { match(it, own.filter { it.frequencyCount > 0 }) },
    )
}
