package com.vpr.screenlate.dictionary.api

import android.content.res.Resources

/**
 * Grammatical tags of form-of entries (Wiktionary dictionaries: `["lemma", ["genitive", "plural"]]`) in the interface
 * language. A tag may hold several words and alternatives joined by slashes ("dative/accusative"); words outside the
 * list stay as the dictionary has them.
 */
object FormOfTags {
    private val STRINGS: Map<String, Int> = mapOf(
"abbreviation" to R.string.form_of_tag_abbreviation,
        "ablative" to R.string.form_of_tag_ablative,
        "accusative" to R.string.form_of_tag_accusative,
        "active" to R.string.form_of_tag_active,
        "adjective" to R.string.form_of_tag_adjective,
        "adverbial" to R.string.form_of_tag_adverbial,
        "alt-of" to R.string.form_of_tag_alt_of,
        "alternative" to R.string.form_of_tag_alternative,
        "animate" to R.string.form_of_tag_animate,
        "aorist" to R.string.form_of_tag_aorist,
        "archaic" to R.string.form_of_tag_archaic,
        "assertive" to R.string.form_of_tag_assertive,
        "attributive" to R.string.form_of_tag_attributive,
        "augmentative" to R.string.form_of_tag_augmentative,
        "causative" to R.string.form_of_tag_causative,
        "colloquial" to R.string.form_of_tag_colloquial,
        "comparative" to R.string.form_of_tag_comparative,
        "conditional" to R.string.form_of_tag_conditional,
        "conjunctive" to R.string.form_of_tag_conjunctive,
        "continuous" to R.string.form_of_tag_continuous,
        "contrastive" to R.string.form_of_tag_contrastive,
        "countable" to R.string.form_of_tag_countable,
        "dative" to R.string.form_of_tag_dative,
        "definite" to R.string.form_of_tag_definite,
        "dependent" to R.string.form_of_tag_dependent,
        "determiner" to R.string.form_of_tag_determiner,
        "dialectal" to R.string.form_of_tag_dialectal,
        "diminutive" to R.string.form_of_tag_diminutive,
        "dual" to R.string.form_of_tag_dual,
        "feminine" to R.string.form_of_tag_feminine,
        "first-person" to R.string.form_of_tag_first_person,
        "formal" to R.string.form_of_tag_formal,
        "future" to R.string.form_of_tag_future,
        "genitive" to R.string.form_of_tag_genitive,
        "gerund" to R.string.form_of_tag_gerund,
        "gerundive" to R.string.form_of_tag_gerundive,
        "historical" to R.string.form_of_tag_historical,
        "hortative" to R.string.form_of_tag_hortative,
        "idiomatic" to R.string.form_of_tag_idiomatic,
        "imperative" to R.string.form_of_tag_imperative,
        "imperfect" to R.string.form_of_tag_imperfect,
        "imperfective" to R.string.form_of_tag_imperfective,
        "inanimate" to R.string.form_of_tag_inanimate,
        "indefinite" to R.string.form_of_tag_indefinite,
        "indicative" to R.string.form_of_tag_indicative,
        "infinitive" to R.string.form_of_tag_infinitive,
        "informal" to R.string.form_of_tag_informal,
        "instrumental" to R.string.form_of_tag_instrumental,
        "interrogative" to R.string.form_of_tag_interrogative,
        "intransitive" to R.string.form_of_tag_intransitive,
        "literary" to R.string.form_of_tag_literary,
        "locative" to R.string.form_of_tag_locative,
        "masculine" to R.string.form_of_tag_masculine,
        "misspelling" to R.string.form_of_tag_misspelling,
        "mixed" to R.string.form_of_tag_mixed,
        "negative" to R.string.form_of_tag_negative,
        "neologism" to R.string.form_of_tag_neologism,
        "neuter" to R.string.form_of_tag_neuter,
        "nominative" to R.string.form_of_tag_nominative,
        "non-past" to R.string.form_of_tag_non_past,
        "nonstandard" to R.string.form_of_tag_nonstandard,
        "object-first-person" to R.string.form_of_tag_object_first_person,
        "object-plural" to R.string.form_of_tag_object_plural,
        "object-second-person" to R.string.form_of_tag_object_second_person,
        "object-singular" to R.string.form_of_tag_object_singular,
        "object-third-person" to R.string.form_of_tag_object_third_person,
        "obsolete" to R.string.form_of_tag_obsolete,
        "participle" to R.string.form_of_tag_participle,
        "partitive" to R.string.form_of_tag_partitive,
        "passive" to R.string.form_of_tag_passive,
        "past" to R.string.form_of_tag_past,
        "perfect" to R.string.form_of_tag_perfect,
        "perfective" to R.string.form_of_tag_perfective,
        "personal" to R.string.form_of_tag_personal,
        "pluperfect" to R.string.form_of_tag_pluperfect,
        "plural" to R.string.form_of_tag_plural,
        "polite" to R.string.form_of_tag_polite,
        "possessive" to R.string.form_of_tag_possessive,
        "predicative" to R.string.form_of_tag_predicative,
        "prepositional" to R.string.form_of_tag_prepositional,
        "present" to R.string.form_of_tag_present,
        "preterite" to R.string.form_of_tag_preterite,
        "progressive" to R.string.form_of_tag_progressive,
        "rare" to R.string.form_of_tag_rare,
        "reflexive" to R.string.form_of_tag_reflexive,
        "second-person" to R.string.form_of_tag_second_person,
        "sequential" to R.string.form_of_tag_sequential,
        "short-form" to R.string.form_of_tag_short_form,
        "simple" to R.string.form_of_tag_simple,
        "singular" to R.string.form_of_tag_singular,
        "slang" to R.string.form_of_tag_slang,
        "strong" to R.string.form_of_tag_strong,
        "subjunctive" to R.string.form_of_tag_subjunctive,
        "subjunctive-i" to R.string.form_of_tag_subjunctive_i,
        "subjunctive-ii" to R.string.form_of_tag_subjunctive_ii,
        "subordinate-clause" to R.string.form_of_tag_subordinate_clause,
        "superlative" to R.string.form_of_tag_superlative,
        "supine" to R.string.form_of_tag_supine,
        "third-person" to R.string.form_of_tag_third_person,
        "transitive" to R.string.form_of_tag_transitive,
        "uncountable" to R.string.form_of_tag_uncountable,
        "vocative" to R.string.form_of_tag_vocative,
        "weak" to R.string.form_of_tag_weak,
        "historic" to R.string.form_of_tag_historical,
        "short" to R.string.form_of_tag_short_form,
    )

    private val SPACES = Regex("\\s+")

    /** [tag] in the interface language; null when none of its words is in the list. */
    fun label(resources: Resources, tag: String): String? = label(tag) { resources.getString(it) }

    internal fun label(tag: String, string: (Int) -> String): String? {
        var translated = false
        val words = tag.trim().split(SPACES).filter { it.isNotEmpty() }.map { word ->
            expandPersons(word.split('/')).joinToString("/") { part ->
                STRINGS[part.lowercase()]?.let { translated = true; string(it) } ?: part
            }
        }
        return if (translated) words.joinToString(" ") else null
    }

    /** Alternatives that share the last one's "-person": first/second-person stands for first-person/second-person. */
    internal fun expandPersons(parts: List<String>): List<String> {
        val last = parts.last()
        if (parts.size < 2 || !last.endsWith(PERSON)) return parts
        return parts.dropLast(1).map { if ('-' in it) it else it + PERSON } + last
    }

    private const val PERSON = "-person"
}
