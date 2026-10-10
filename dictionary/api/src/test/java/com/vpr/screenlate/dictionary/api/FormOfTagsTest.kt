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
