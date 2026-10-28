package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormOfTagsTest {
    /** Resource names instead of the texts, so the test shows which string each word took. */
    private val names: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }

    private fun label(
        tag: String,
        aliases: Map<String, String> = emptyMap(),
        tagNames: Map<String, String> = emptyMap(),
    ) = FormOfTags.label(tag, { names.getValue(it).removePrefix("form_of_tag_") }, aliases, tagNames) { "country:$it" }

    @Test
    fun `every word of a tag is translated`() {
        assertThat(label("genitive")).isEqualTo("genitive")
        assertThat(label("Genitive  plural")).isEqualTo("genitive plural")
        assertThat(label("alt-of")).isEqualTo("alt_of")
    }

    @Test
    fun `a language's aliases name a word as the word it stands for`() {
        val hungarian = mapOf("definite" to "object-definite", "indefinite" to "object-indefinite")
        assertThat(label("first-person singular definite/indefinite past indicative", hungarian))
            .isEqualTo("first_person singular object_definite/object_indefinite past indicative")
        assertThat(label("Definite", hungarian)).isEqualTo("object_definite")
        assertThat(label("singular definite")).isEqualTo("singular definite")
    }

    @Test
    fun `a language's tag names name a whole tag only`() {
        val vietnamese = mapOf("central" to "central-vietnam")
        assertThat(label("Central", tagNames = vietnamese)).isEqualTo("central_vietnam")
        assertThat(label("alternative Central North Vietnam", tagNames = vietnamese))
            .isEqualTo("alternative central northern country:VN")
        assertThat(label("Central")).isEqualTo("central")
    }

    @Test
    fun `words outside the list stay as they are`() {
        assertThat(label("genitive ezafe")).isEqualTo("genitive ezafe")
        assertThat(label("ezafe")).isNull()
        assertThat(label("  ")).isNull()
    }

    @Test
    fun `alternatives are translated one by one`() {
        assertThat(label("dative/accusative")).isEqualTo("dative/accusative")
        assertThat(label("first/second-person")).isEqualTo("first_person/second_person")
        assertThat(label("first/third-person singular")).isEqualTo("first_person/third_person singular")
    }

    @Test
    fun `scripts, varieties and regions of chinese entries are translated`() {
        assertThat(label("alternative Traditional-Chinese Hokkien")).isEqualTo("alternative traditional_chinese hokkien")
        assertThat(label("Second-Round-Simplified-Chinese Simplified-Chinese"))
            .isEqualTo("second_round_simplified_chinese simplified_chinese")
        assertThat(label("Hong-Kong")).isEqualTo("hong_kong")
        assertThat(label("name")).isEqualTo("name")
    }

    @Test
    fun `korean scripts, forms and regions are translated, two-word tags as one`() {
        assertThat(label("hanja form")).isEqualTo("hanja_form")
        assertThat(label("first hanja form")).isEqualTo("first hanja_form")
        assertThat(label("formal past noun-from-verb")).isEqualTo("formal past noun_from_verb")
        assertThat(label("present verbal noun")).isEqualTo("present verbal_noun")
        assertThat(label("alternative Early Modern")).isEqualTo("alternative early_modern")
        assertThat(label("alternative North-Korea Yanbian dialectal")).isEqualTo("alternative north_korea yanbian dialectal")
        assertThat(label("hangeul")).isEqualTo("hangeul")
        assertThat(label("Yale")).isNull()
    }

    @Test
    fun `finnish cases, moods and infinitives are translated`() {
        assertThat(label("singular inessive")).isEqualTo("singular inessive")
        assertThat(label("plural illative")).isEqualTo("plural illative")
        assertThat(label("third-person singular present potential")).isEqualTo("third_person singular present potential")
        assertThat(label("past infinitive-i-long")).isEqualTo("past infinitive_i_long")
        assertThat(label("third-person elative")).isEqualTo("third_person elative")
        assertThat(label("noun-from-verb negative fourth-person")).isEqualTo("noun_from_verb negative fourth_person")
        assertThat(label("agent")).isEqualTo("agent")
        assertThat(label("present connegative")).isEqualTo("present connegative")
    }

    @Test
    fun `hungarian cases and conjugations are translated`() {
        assertThat(label("singular causal-final")).isEqualTo("singular causal_final")
        assertThat(label("plural terminative")).isEqualTo("plural terminative")
        assertThat(label("singular essive-formal")).isEqualTo("singular essive_formal")
        assertThat(label("plural essive-modal")).isEqualTo("plural essive_modal")
        assertThat(label("first-person singular past indicative object-definite"))
            .isEqualTo("first_person singular past indicative object_definite")
        assertThat(label("first-person singular present indicative object-indefinite"))
            .isEqualTo("first_person singular present indicative object_indefinite")
        assertThat(label("singular non-attributive possessive")).isEqualTo("singular non_attributive possessive")
        assertThat(label("exaggerated")).isEqualTo("exaggerated")
        assertThat(label("singular temporal")).isEqualTo("singular temporal")
    }

    @Test
    fun `estonian moods and infinitives are translated`() {
        assertThat(label("perfect passive negative quotative")).isEqualTo("perfect passive negative quotative")
        assertThat(label("present infinitive infinitive-da")).isEqualTo("present infinitive infinitive_da")
        assertThat(label("active infinitive inessive infinitive-ma")).isEqualTo("active infinitive inessive infinitive_ma")
        assertThat(label("da-infinitive")).isEqualTo("infinitive_da")
        assertThat(label("first/second/third-person plural present indicative negative"))
            .isEqualTo("first_person/second_person/third_person plural present indicative negative")
        assertThat(label("singular terminative")).isEqualTo("singular terminative")
    }

    @Test
    fun `azerbaijani tenses, moods, participles and scripts are translated`() {
        assertThat(label("first-person singular definite indicative past-future"))
            .isEqualTo("first_person singular definite indicative past_future")
        assertThat(label("third-person singular present perfect indicative perfect-ii"))
            .isEqualTo("third_person singular present perfect indicative perfect_ii")
        assertThat(label("first-person singular present evidential")).isEqualTo("first_person singular present evidential")
        assertThat(label("first-person singular present obligative")).isEqualTo("first_person singular present obligative")
        assertThat(label("participle negative non-subject")).isEqualTo("participle negative non_subject")
        assertThat(label("non-past participle subject")).isEqualTo("non_past participle subjective")
        assertThat(label("participle general temporal")).isEqualTo("participle general temporal")
        assertThat(label("nominative plural broken-form")).isEqualTo("nominative plural broken_form")
        assertThat(label("Cyrillic")).isEqualTo("cyrillic")
    }

    @Test
    fun `uzbek habitual and intentive forms are translated`() {
        assertThat(label("third-person singular present indicative negative continuative habitual"))
            .isEqualTo("third_person singular present indicative negative continuous habitual")
        assertThat(label("plural intentive")).isEqualTo("plural intentive")
    }

    @Test
    fun `gloss left after two spaces is cut off`() {
        assertThat(FormOfTags.clean("hanja form   the Korean language”)")).isEqualTo("hanja form")
        assertThat(FormOfTags.clean("hanja form  ”)")).isEqualTo("hanja form")
        assertThat(FormOfTags.clean(" present adnominal   see -는")).isEqualTo("present adnominal")
        assertThat(FormOfTags.clean("genitive plural")).isEqualTo("genitive plural")
    }

    @Test
    fun `other spellings share the word's string`() {
        assertThat(label("historic")).isEqualTo("historical")
        assertThat(label("short")).isEqualTo("short_form")
    }

    @Test
    fun `a person is added only to alternatives without one`() {
        assertThat(FormOfTags.expandPersons(listOf("first", "second-person")))
            .containsExactly("first-person", "second-person").inOrder()
        assertThat(FormOfTags.expandPersons(listOf("object-first", "second-person")))
            .containsExactly("object-first", "second-person").inOrder()
        assertThat(FormOfTags.expandPersons(listOf("dative", "accusative"))).containsExactly("dative", "accusative").inOrder()
        assertThat(FormOfTags.expandPersons(listOf("third-person"))).containsExactly("third-person")
    }

    @Test
    fun `spanish, german and other european tags are translated`() {
        assertThat(label("informal second-person singular imperative with-tú"))
            .isEqualTo("informal second_person singular imperative with_tu")
        assertThat(label("third-person plural imperfect subjunctive imperfect-se"))
            .isEqualTo("third_person plural imperfect subjunctive imperfect_se")
        assertThat(label("dative plural strong without-article")).isEqualTo("dative plural strong without_article")
        assertThat(label("female equivalent")).isEqualTo("female_equivalent")
        assertThat(label("agent noun")).isEqualTo("agent_noun")
    }

    @Test
    fun `polish, russian, turkish and vietnamese tags are translated`() {
        assertThat(label("plural conditional virile")).isEqualTo("plural conditional virile")
        assertThat(label("alternative Middle Polish Łowicz")).isEqualTo("alternative middle_polish Łowicz")
        assertThat(label("adverb derived")).isEqualTo("derived_adverb")
        assertThat(label("adjective relational")).isEqualTo("adjective relational")
        assertThat(label("imperfective determinate")).isEqualTo("imperfective determinate")
        assertThat(label("accusative second/third-person singular definite possessed-many possessive"))
            .isEqualTo("accusative second_person/third_person singular definite possessed_many possessive")
        assertThat(label("third-person singular continuative")).isEqualTo("third_person singular continuous")
        assertThat(label("Hán-Nôm")).isEqualTo("han_nom")
        assertThat(label("CJK")).isEqualTo("cjk")
        assertThat(label("alternative Central North Vietnam")).isEqualTo("alternative central northern country:VN")
    }

    @Test
    fun `a word its neighbor already says is left out`() {
        assertThat(label("dative plural superlative strong degree")).isEqualTo("dative plural superlative strong")
        assertThat(label("degree")).isNull()
        assertThat(label("adverbial form")).isEqualTo("adverbial")
        assertThat(label("hanja form")).isEqualTo("hanja_form")
    }

    @Test
    fun `latvian moods are translated`() {
        assertThat(label("indicative debitive")).isEqualTo("indicative debitive")
        assertThat(label("past renarrative")).isEqualTo("past renarrative")
    }

    @Test
    fun `dutch, czech, serbo-croatian, tagalog, belarusian and greek tags are translated`() {
        assertThat(label("first/second-person singular present main-clause"))
            .isEqualTo("first_person/second_person singular present main_clause")
        assertThat(label("plural past indicative dependent-clause"))
            .isEqualTo("plural past indicative subordinate_clause")
        assertThat(label("second-person singular past Flanders colloquial"))
            .isEqualTo("second_person singular past flanders colloquial")
        assertThat(label("singular masculine present transgressive"))
            .isEqualTo("singular masculine present transgressive")
        assertThat(label("iterative")).isEqualTo("frequentative")
        assertThat(label("singular conditional conditional-ii")).isEqualTo("singular conditional conditional_ii")
        assertThat(label("alternative Ikavian Čakavian")).isEqualTo("alternative ikavian chakavian")
        assertThat(label("infinitive benefactive")).isEqualTo("infinitive benefactive")
        assertThat(label("plural count-form")).isEqualTo("plural count_form")
        assertThat(label("count form")).isEqualTo("count_form")
        assertThat(label("alternative Taraškievica")).isEqualTo("alternative taraskievica")
        assertThat(label("active infinitive-aorist")).isEqualTo("active infinitive_aorist")
    }

    @Test
    fun `countries take the system's name, other regions a string`() {
        assertThat(label("alternative Brazil pronunciation-spelling"))
            .isEqualTo("alternative country:BR pronunciation_spelling")
        assertThat(label("alternative Liechtenstein Switzerland")).isEqualTo("alternative country:LI country:CH")
        assertThat(label("alternative Brunei Malaysia Singapore"))
            .isEqualTo("alternative country:BN country:MY country:SG")
        assertThat(label("alternative Bosnia Serbia")).isEqualTo("alternative country:BA country:RS")
        assertThat(label("Latin-America")).isEqualTo("latin_america")
    }
}
