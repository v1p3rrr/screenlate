package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormOfTagsTest {
    /** Resource names instead of the texts, so the test shows which string each word took. */
    private val names: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }

    private fun label(tag: String) = FormOfTags.label(tag) { names.getValue(it).removePrefix("form_of_tag_") }

    @Test
    fun `every word of a tag is translated`() {
        assertThat(label("genitive")).isEqualTo("genitive")
        assertThat(label("Genitive  plural")).isEqualTo("genitive plural")
        assertThat(label("alt-of")).isEqualTo("alt_of")
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
}
