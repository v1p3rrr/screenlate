package com.vpr.screenlate.dictionary.api

import android.content.res.Resources
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes

/**
 * Chips of Wiktionary dictionaries for a part of one country. wty's shared tag table names them by the country's flag
 * and a direction (🇧🇷🎯 "Central" for central Brazil, 🇩🇪⬅️ "western Germany") and gives the same chip to the same word
 * in every language, so Vietnamese, German and Chinese "Central" carry Brazil's flag and English "Western" Germany's.
 * In a dictionary of a language without that country among its flags, such a chip shows the direction instead.
 */
object RegionChips {
    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF
    private const val VARIATION_SELECTOR = 0xFE0F

    /** Direction marks after the flag, as form-of tag words. */
    private val DIRECTIONS = mapOf(
        0x1F3AF to "central",
        0x2B05 to "western",
        0x27A1 to "eastern",
        0x2B06 to "northern",
        0x2B07 to "southern",
        0x2197 to "northeastern",
    )

    /** The country code of [tag]'s flag and the words of its directions; null for a flag alone or any other tag. */
    internal fun parse(tag: String): Pair<String, List<String>>? {
        val points = tag.codePoints().toArray()
        if (points.size < 3 || points.take(2).any { it !in REGIONAL_A..REGIONAL_Z }) return null
        val country = points.take(2).joinToString("") { ('a' + (it - REGIONAL_A)).toString() }
        val words = points.drop(2).filter { it != VARIATION_SELECTOR }.map { DIRECTIONS[it] ?: return null }
        return if (words.isEmpty()) null else country to words
    }

    /** Tag name to the words its chip shows instead (`central`), for the [tags] whose flag is none of [countries]. */
    internal fun foreign(tags: Collection<String>, countries: Collection<String>): Map<String, String> =
        tags.mapNotNull { tag ->
            parse(tag)?.takeIf { (country, _) -> country !in countries }?.let { (_, words) -> tag to words.joinToString(" ") }
        }.toMap()

    /**
     * [notes] with the labels of its chips that name a region of another language's country, in [resources]' language
     * and the dictionary language's own tag names (Vietnamese central reads Central Vietnam); such a chip loses its
     * note, which names the other country's region. A dictionary of an unknown language keeps its chips.
     */
    fun labelled(notes: DictionaryTagNotes, resources: Resources): DictionaryTagNotes {
        val languages = Language.withDictionaries(notes.sourceLanguage)
        if (languages.isEmpty()) return notes
        val foreign = foreign(notes.notes.keys, languages.flatMap { it.support.flagCountries })
        if (foreign.isEmpty()) return notes
        val support = languages.first().support
        val labels = foreign.mapValues { (_, words) ->
            FormOfTags.label(resources, words, support.formTagAliases, support.formTagNames) ?: words
        }
        return notes.copy(notes = notes.notes - foreign.keys, labels = notes.labels + labels)
    }
}
