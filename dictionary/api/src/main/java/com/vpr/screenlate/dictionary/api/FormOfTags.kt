package com.vpr.screenlate.dictionary.api

import android.content.res.Resources
import java.util.Locale

/**
 * Grammatical tags of form-of entries (Wiktionary dictionaries: `["lemma", ["genitive", "plural"]]`) in the interface
 * language. A tag may hold several words and alternatives joined by slashes ("dative/accusative"); words outside the
 * list stay as the dictionary has them.
 */
object FormOfTags {
    private val STRINGS: Map<String, Int> = mapOf(
        "abbreviation" to R.string.form_of_tag_abbreviation,
        "ablative" to R.string.form_of_tag_ablative,
        "abstract-noun" to R.string.form_of_tag_abstract_noun,
        "accusative" to R.string.form_of_tag_accusative,
        "active" to R.string.form_of_tag_active,
        "adjectival" to R.string.form_of_tag_adjectival,
        "adjective" to R.string.form_of_tag_adjective,
        "adnominal" to R.string.form_of_tag_adnominal,
        "adverb" to R.string.form_of_tag_adverb,
        "adverbial" to R.string.form_of_tag_adverbial,
        "all-case" to R.string.form_of_tag_all_case,
        "all-gender" to R.string.form_of_tag_all_gender,
        "alt-of" to R.string.form_of_tag_alt_of,
        "alternative" to R.string.form_of_tag_alternative,
        "animate" to R.string.form_of_tag_animate,
        "anterior" to R.string.form_of_tag_anterior,
        "aorist" to R.string.form_of_tag_aorist,
        "apocopic" to R.string.form_of_tag_apocopic,
        "archaic" to R.string.form_of_tag_archaic,
        "assertive" to R.string.form_of_tag_assertive,
        "attributive" to R.string.form_of_tag_attributive,
        "augmentative" to R.string.form_of_tag_augmentative,
        "before-vowel" to R.string.form_of_tag_before_vowel,
        "by-personal-gender" to R.string.form_of_tag_by_personal_gender,
        "cantonese" to R.string.form_of_tag_cantonese,
        "causative" to R.string.form_of_tag_causative,
        "central" to R.string.form_of_tag_central,
        "china" to R.string.form_of_tag_china,
        "chinese" to R.string.form_of_tag_chinese,
        "cjk" to R.string.form_of_tag_cjk,
        "classifier" to R.string.form_of_tag_classifier,
        "clipping" to R.string.form_of_tag_clipping,
        "collective" to R.string.form_of_tag_collective,
        "colloquial" to R.string.form_of_tag_colloquial,
        "common" to R.string.form_of_tag_common,
        "comparative" to R.string.form_of_tag_comparative,
        "conditional" to R.string.form_of_tag_conditional,
        "conjunctive" to R.string.form_of_tag_conjunctive,
        "connective" to R.string.form_of_tag_connective,
        "contemporary" to R.string.form_of_tag_contemporary,
        "continuous" to R.string.form_of_tag_continuous,
        "contraction" to R.string.form_of_tag_contraction,
        "contrastive" to R.string.form_of_tag_contrastive,
        "countable" to R.string.form_of_tag_countable,
        "counter" to R.string.form_of_tag_counter,
        "dated" to R.string.form_of_tag_dated,
        "dative" to R.string.form_of_tag_dative,
        "debitive" to R.string.form_of_tag_debitive,
        "definite" to R.string.form_of_tag_definite,
        "demonym" to R.string.form_of_tag_demonym,
        "dependent" to R.string.form_of_tag_dependent,
        "derogatory" to R.string.form_of_tag_derogatory,
        "determinate" to R.string.form_of_tag_determinate,
        "determiner" to R.string.form_of_tag_determiner,
        "dialectal" to R.string.form_of_tag_dialectal,
        "diminutive" to R.string.form_of_tag_diminutive,
        "dual" to R.string.form_of_tag_dual,
        "emphatic" to R.string.form_of_tag_emphatic,
        "endearing" to R.string.form_of_tag_endearing,
        "eumhun" to R.string.form_of_tag_eumhun,
        "euphemistic" to R.string.form_of_tag_euphemistic,
        "feminine" to R.string.form_of_tag_feminine,
        "first-person" to R.string.form_of_tag_first_person,
        "formal" to R.string.form_of_tag_formal,
        "frequentative" to R.string.form_of_tag_frequentative,
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
        "hán-nôm" to R.string.form_of_tag_han_nom,
        "idiomatic" to R.string.form_of_tag_idiomatic,
        "imperative" to R.string.form_of_tag_imperative,
        "imperfect" to R.string.form_of_tag_imperfect,
        "imperfect-se" to R.string.form_of_tag_imperfect_se,
        "imperfective" to R.string.form_of_tag_imperfective,
        "impersonal" to R.string.form_of_tag_impersonal,
        "inanimate" to R.string.form_of_tag_inanimate,
        "indeclinable" to R.string.form_of_tag_indeclinable,
        "indefinite" to R.string.form_of_tag_indefinite,
        "indeterminate" to R.string.form_of_tag_indeterminate,
        "indicative" to R.string.form_of_tag_indicative,
        "inferential" to R.string.form_of_tag_inferential,
        "infinitive" to R.string.form_of_tag_infinitive,
        "infinitive-zu" to R.string.form_of_tag_infinitive_zu,
        "informal" to R.string.form_of_tag_informal,
        "initialism" to R.string.form_of_tag_initialism,
        "instrumental" to R.string.form_of_tag_instrumental,
        "internet" to R.string.form_of_tag_internet,
        "interrogative" to R.string.form_of_tag_interrogative,
        "intransitive" to R.string.form_of_tag_intransitive,
        "invariable" to R.string.form_of_tag_invariable,
        "irregular" to R.string.form_of_tag_irregular,
        "jeolla" to R.string.form_of_tag_jeolla,
        "latin-america" to R.string.form_of_tag_latin_america,
        "literary" to R.string.form_of_tag_literary,
        "locative" to R.string.form_of_tag_locative,
        "long-form" to R.string.form_of_tag_long_form,
        "louisiana" to R.string.form_of_tag_louisiana,
        "lowercase" to R.string.form_of_tag_lowercase,
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
        "necessitative" to R.string.form_of_tag_necessitative,
        "negative" to R.string.form_of_tag_negative,
        "neologism" to R.string.form_of_tag_neologism,
        "neuter" to R.string.form_of_tag_neuter,
        "no-plural" to R.string.form_of_tag_no_plural,
        "nominative" to R.string.form_of_tag_nominative,
        "non-past" to R.string.form_of_tag_non_past,
        "non-prospective" to R.string.form_of_tag_non_prospective,
        "nonstandard" to R.string.form_of_tag_nonstandard,
        "nonvirile" to R.string.form_of_tag_nonvirile,
        "north-korea" to R.string.form_of_tag_north_korea,
        "northern" to R.string.form_of_tag_northern,
        "not-comparable" to R.string.form_of_tag_not_comparable,
        "noun-from-verb" to R.string.form_of_tag_noun_from_verb,
        "object-first-person" to R.string.form_of_tag_object_first_person,
        "object-plural" to R.string.form_of_tag_object_plural,
        "object-second-person" to R.string.form_of_tag_object_second_person,
        "object-singular" to R.string.form_of_tag_object_singular,
        "object-third-person" to R.string.form_of_tag_object_third_person,
        "obsolete" to R.string.form_of_tag_obsolete,
        "optative" to R.string.form_of_tag_optative,
        "participle" to R.string.form_of_tag_participle,
        "partitive" to R.string.form_of_tag_partitive,
        "passive" to R.string.form_of_tag_passive,
        "past" to R.string.form_of_tag_past,
        "pejorative" to R.string.form_of_tag_pejorative,
        "perfect" to R.string.form_of_tag_perfect,
        "perfective" to R.string.form_of_tag_perfective,
        "personal" to R.string.form_of_tag_personal,
        "pluperfect" to R.string.form_of_tag_pluperfect,
        "plural" to R.string.form_of_tag_plural,
        "plural-only" to R.string.form_of_tag_plural_only,
        "poetic" to R.string.form_of_tag_poetic,
        "polite" to R.string.form_of_tag_polite,
        "possessed-many" to R.string.form_of_tag_possessed_many,
        "possessed-single" to R.string.form_of_tag_possessed_single,
        "possessive" to R.string.form_of_tag_possessive,
        "predicative" to R.string.form_of_tag_predicative,
        "prepositional" to R.string.form_of_tag_prepositional,
        "present" to R.string.form_of_tag_present,
        "preterite" to R.string.form_of_tag_preterite,
        "progressive" to R.string.form_of_tag_progressive,
        "pronoun" to R.string.form_of_tag_pronoun,
        "pronunciation-spelling" to R.string.form_of_tag_pronunciation_spelling,
        "proper-noun" to R.string.form_of_tag_proper_noun,
        "proscribed" to R.string.form_of_tag_proscribed,
        "prospective" to R.string.form_of_tag_prospective,
        "quebec" to R.string.form_of_tag_quebec,
        "rare" to R.string.form_of_tag_rare,
        "reduplication" to R.string.form_of_tag_reduplication,
        "reflexive" to R.string.form_of_tag_reflexive,
        "regional" to R.string.form_of_tag_regional,
        "relational" to R.string.form_of_tag_relational,
        "renarrative" to R.string.form_of_tag_renarrative,
        "revised" to R.string.form_of_tag_revised,
        "romanization" to R.string.form_of_tag_romanization,
        "second-person" to R.string.form_of_tag_second_person,
        "second-person-semantically" to R.string.form_of_tag_second_person_semantically,
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
        "surname" to R.string.form_of_tag_surname,
        "taiwan" to R.string.form_of_tag_taiwan,
        "teochew" to R.string.form_of_tag_teochew,
        "third-person" to R.string.form_of_tag_third_person,
        "traditional" to R.string.form_of_tag_traditional,
        "traditional-chinese" to R.string.form_of_tag_traditional_chinese,
        "transitive" to R.string.form_of_tag_transitive,
        "tuscany" to R.string.form_of_tag_tuscany,
        "uncommon" to R.string.form_of_tag_uncommon,
        "uncountable" to R.string.form_of_tag_uncountable,
        "uppercase" to R.string.form_of_tag_uppercase,
        "virile" to R.string.form_of_tag_virile,
        "vocative" to R.string.form_of_tag_vocative,
        "vos-form" to R.string.form_of_tag_vos_form,
        "voseo" to R.string.form_of_tag_voseo,
        "vulgar" to R.string.form_of_tag_vulgar,
        "weak" to R.string.form_of_tag_weak,
        "with-tú" to R.string.form_of_tag_with_tu,
        "with-vos" to R.string.form_of_tag_with_vos,
        "without-article" to R.string.form_of_tag_without_article,
        "wu" to R.string.form_of_tag_wu,
        "yanbian" to R.string.form_of_tag_yanbian,
        "yukjin" to R.string.form_of_tag_yukjin,
        "zu-infinitive" to R.string.form_of_tag_zu_infinitive,
        "historic" to R.string.form_of_tag_historical,
        "short" to R.string.form_of_tag_short_form,
        "continuative" to R.string.form_of_tag_continuous,
        "north" to R.string.form_of_tag_northern,
        "south" to R.string.form_of_tag_southern,
    )

    /** Two-word tags translated as one: "hanja form" reads as "漢字表記", not word by word. */
    private val PHRASES: Map<String, Int> = mapOf(
        "adverb derived" to R.string.form_of_tag_derived_adverb,
        "agent noun" to R.string.form_of_tag_agent_noun,
        "early modern" to R.string.form_of_tag_early_modern,
        "female equivalent" to R.string.form_of_tag_female_equivalent,
        "hanja form" to R.string.form_of_tag_hanja_form,
        "middle polish" to R.string.form_of_tag_middle_polish,
        "verbal noun" to R.string.form_of_tag_verbal_noun,
    )

    /** Words a neighbor already says: "superlative degree" reads as the superlative, "rare form" as rare. */
    private val SILENT = setOf("degree", "form")

    /** Countries in regional tags, named in the interface language by the system. */
    private val COUNTRIES: Map<String, String> = mapOf(
        "angola" to "AO", "argentina" to "AR", "austria" to "AT", "belgium" to "BE", "bolivia" to "BO",
        "brazil" to "BR", "canada" to "CA", "chile" to "CL", "colombia" to "CO", "costa-rica" to "CR", "cuba" to "CU",
        "dominican-republic" to "DO", "ecuador" to "EC", "el-salvador" to "SV", "france" to "FR", "germany" to "DE",
        "guatemala" to "GT", "honduras" to "HN", "italy" to "IT", "liechtenstein" to "LI", "lithuania" to "LT",
        "luxembourg" to "LU",
        "mexico" to "MX", "mozambique" to "MZ", "nicaragua" to "NI", "panama" to "PA", "paraguay" to "PY",
        "peru" to "PE", "philippines" to "PH", "portugal" to "PT", "puerto-rico" to "PR", "spain" to "ES",
        "switzerland" to "CH", "uruguay" to "UY", "us" to "US", "venezuela" to "VE", "vietnam" to "VN",
    )

    private val SPACES = Regex("\\s+")

    /**
     * [tag] without a piece of gloss that a dictionary's converter left after two or more spaces ("hanja form   the
     * Korean language”)" in Wiktionary's Korean dictionary); tags have single spaces only.
     */
    fun clean(tag: String): String {
        val trimmed = tag.trim()
        val end = trimmed.indexOf("  ")
        return if (end < 0) trimmed else trimmed.substring(0, end)
    }

    /** [tag] in the interface language; null when none of its words is in the list. */
    fun label(resources: Resources, tag: String): String? {
        val locale = resources.configuration.locales[0]
        return label(tag, { resources.getString(it) }) {
            Locale.Builder().setRegion(it).build().getDisplayCountry(locale)
        }
    }

    /** [country] names a region code (`ES`). */
    internal fun label(tag: String, string: (Int) -> String, country: (String) -> String = { it }): String? {
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
            if (words[index].lowercase() in SILENT) {
                index++
                continue
            }
            labels += expandPersons(words[index].split('/')).joinToString("/") { part ->
                val key = part.lowercase()
                STRINGS[key]?.let { translated = true; string(it) }
                    ?: COUNTRIES[key]?.let { translated = true; country(it) }
                    ?: part
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
