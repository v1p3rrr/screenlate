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
        "adnominal" to R.string.form_of_tag_adnominal,
        "adverbial" to R.string.form_of_tag_adverbial,
        "alt-of" to R.string.form_of_tag_alt_of,
        "alternative" to R.string.form_of_tag_alternative,
        "animate" to R.string.form_of_tag_animate,
        "aorist" to R.string.form_of_tag_aorist,
        "archaic" to R.string.form_of_tag_archaic,
        "assertive" to R.string.form_of_tag_assertive,
        "attributive" to R.string.form_of_tag_attributive,
        "augmentative" to R.string.form_of_tag_augmentative,
        "cantonese" to R.string.form_of_tag_cantonese,
        "causative" to R.string.form_of_tag_causative,
        "china" to R.string.form_of_tag_china,
        "chinese" to R.string.form_of_tag_chinese,
        "colloquial" to R.string.form_of_tag_colloquial,
        "common" to R.string.form_of_tag_common,
        "comparative" to R.string.form_of_tag_comparative,
        "conditional" to R.string.form_of_tag_conditional,
        "conjunctive" to R.string.form_of_tag_conjunctive,
        "connective" to R.string.form_of_tag_connective,
        "continuous" to R.string.form_of_tag_continuous,
        "contraction" to R.string.form_of_tag_contraction,
        "contrastive" to R.string.form_of_tag_contrastive,
        "countable" to R.string.form_of_tag_countable,
        "counter" to R.string.form_of_tag_counter,
        "dated" to R.string.form_of_tag_dated,
        "dative" to R.string.form_of_tag_dative,
        "definite" to R.string.form_of_tag_definite,
        "dependent" to R.string.form_of_tag_dependent,
        "determiner" to R.string.form_of_tag_determiner,
        "dialectal" to R.string.form_of_tag_dialectal,
        "diminutive" to R.string.form_of_tag_diminutive,
        "dual" to R.string.form_of_tag_dual,
        "eumhun" to R.string.form_of_tag_eumhun,
        "euphemistic" to R.string.form_of_tag_euphemistic,
        "feminine" to R.string.form_of_tag_feminine,
        "first-person" to R.string.form_of_tag_first_person,
        "formal" to R.string.form_of_tag_formal,
        "future" to R.string.form_of_tag_future,
        "gangwon" to R.string.form_of_tag_gangwon,
        "genitive" to R.string.form_of_tag_genitive,
        "gerund" to R.string.form_of_tag_gerund,
        "gerundive" to R.string.form_of_tag_gerundive,
        "gyeongsang" to R.string.form_of_tag_gyeongsang,
        "hakka" to R.string.form_of_tag_hakka,
        "hamgyong" to R.string.form_of_tag_hamgyong,
        "hangeul" to R.string.form_of_tag_hangeul,
        "hanja" to R.string.form_of_tag_hanja,
        "historical" to R.string.form_of_tag_historical,
        "hokkien" to R.string.form_of_tag_hokkien,
        "hong-kong" to R.string.form_of_tag_hong_kong,
        "honorific" to R.string.form_of_tag_honorific,
        "hortative" to R.string.form_of_tag_hortative,
        "humorous" to R.string.form_of_tag_humorous,
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
        "internet" to R.string.form_of_tag_internet,
        "interrogative" to R.string.form_of_tag_interrogative,
        "intransitive" to R.string.form_of_tag_intransitive,
        "jeolla" to R.string.form_of_tag_jeolla,
        "literary" to R.string.form_of_tag_literary,
        "locative" to R.string.form_of_tag_locative,
        "mainland-china" to R.string.form_of_tag_mainland_china,
        "mandarin" to R.string.form_of_tag_mandarin,
        "masculine" to R.string.form_of_tag_masculine,
        "min" to R.string.form_of_tag_min,
        "min-dong" to R.string.form_of_tag_min_dong,
        "min-nan" to R.string.form_of_tag_min_nan,
        "misspelling" to R.string.form_of_tag_misspelling,
        "mixed" to R.string.form_of_tag_mixed,
        "morpheme" to R.string.form_of_tag_morpheme,
        "motive-form" to R.string.form_of_tag_motive_form,
        "name" to R.string.form_of_tag_name,
        "negative" to R.string.form_of_tag_negative,
        "neologism" to R.string.form_of_tag_neologism,
        "neuter" to R.string.form_of_tag_neuter,
        "nominative" to R.string.form_of_tag_nominative,
        "non-past" to R.string.form_of_tag_non_past,
        "nonstandard" to R.string.form_of_tag_nonstandard,
        "north-korea" to R.string.form_of_tag_north_korea,
        "northern" to R.string.form_of_tag_northern,
        "noun-from-verb" to R.string.form_of_tag_noun_from_verb,
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
        "proscribed" to R.string.form_of_tag_proscribed,
        "rare" to R.string.form_of_tag_rare,
        "reflexive" to R.string.form_of_tag_reflexive,
        "revised" to R.string.form_of_tag_revised,
        "romanization" to R.string.form_of_tag_romanization,
        "second-person" to R.string.form_of_tag_second_person,
        "second-round-simplified-chinese" to R.string.form_of_tag_second_round_simplified_chinese,
        "seoul" to R.string.form_of_tag_seoul,
        "sequential" to R.string.form_of_tag_sequential,
        "short-form" to R.string.form_of_tag_short_form,
        "simple" to R.string.form_of_tag_simple,
        "simplified-chinese" to R.string.form_of_tag_simplified_chinese,
        "singular" to R.string.form_of_tag_singular,
        "slang" to R.string.form_of_tag_slang,
        "south-korea" to R.string.form_of_tag_south_korea,
        "southern" to R.string.form_of_tag_southern,
        "standard" to R.string.form_of_tag_standard,
        "strong" to R.string.form_of_tag_strong,
        "subjunctive" to R.string.form_of_tag_subjunctive,
        "subjunctive-i" to R.string.form_of_tag_subjunctive_i,
        "subjunctive-ii" to R.string.form_of_tag_subjunctive_ii,
        "subordinate-clause" to R.string.form_of_tag_subordinate_clause,
        "superlative" to R.string.form_of_tag_superlative,
        "supine" to R.string.form_of_tag_supine,
        "taiwan" to R.string.form_of_tag_taiwan,
        "teochew" to R.string.form_of_tag_teochew,
        "third-person" to R.string.form_of_tag_third_person,
        "traditional-chinese" to R.string.form_of_tag_traditional_chinese,
        "transitive" to R.string.form_of_tag_transitive,
        "uncommon" to R.string.form_of_tag_uncommon,
        "uncountable" to R.string.form_of_tag_uncountable,
        "vocative" to R.string.form_of_tag_vocative,
        "vulgar" to R.string.form_of_tag_vulgar,
        "weak" to R.string.form_of_tag_weak,
        "wu" to R.string.form_of_tag_wu,
        "yanbian" to R.string.form_of_tag_yanbian,
        "yukjin" to R.string.form_of_tag_yukjin,
        "historic" to R.string.form_of_tag_historical,
        "short" to R.string.form_of_tag_short_form,
    )

    /** Two-word tags translated as one: "hanja form" reads as "漢字表記", not word by word. */
    private val PHRASES: Map<String, Int> = mapOf(
        "early modern" to R.string.form_of_tag_early_modern,
        "hanja form" to R.string.form_of_tag_hanja_form,
        "verbal noun" to R.string.form_of_tag_verbal_noun,
    )

    private val SPACES = Regex("\\s+")

    /** [tag] in the interface language; null when none of its words is in the list. */
    fun label(resources: Resources, tag: String): String? = label(tag) { resources.getString(it) }

    internal fun label(tag: String, string: (Int) -> String): String? {
        var translated = false
        val words = tag.trim().split(SPACES).filter { it.isNotEmpty() }
        val labels = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            val phrase = words.getOrNull(index + 1)?.let { PHRASES["${words[index]} $it".lowercase()] }
            if (phrase != null) {
                translated = true
                labels += string(phrase)
                index += 2
                continue
            }
            labels += expandPersons(words[index].split('/')).joinToString("/") { part ->
                STRINGS[part.lowercase()]?.let { translated = true; string(it) } ?: part
            }
            index++
        }
        return if (translated) labels.joinToString(" ") else null
    }

    /** Alternatives that share the last one's "-person": first/second-person stands for first-person/second-person. */
    internal fun expandPersons(parts: List<String>): List<String> {
        val last = parts.last()
        if (parts.size < 2 || !last.endsWith(PERSON)) return parts
        return parts.dropLast(1).map { if ('-' in it) it else it + PERSON } + last
    }

    private const val PERSON = "-person"
}
