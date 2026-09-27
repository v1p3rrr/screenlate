package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class LanguageOptionsTest {

    @Test
    fun `languages are named in their own language, sorted, with flags where one fits`() {
        val options = LanguageOptions.of(listOf("ru", "en", "pt-BR", "eo").map(Locale::forLanguageTag))
        assertThat(options.map { it.tag }).containsExactly("en", "eo", "pt-BR", "ru").inOrder()
        assertThat(options.first { it.tag == "ru" }.name).isEqualTo("Русский")
        assertThat(options.first { it.tag == "en" }.name).isEqualTo("English")
        assertThat(options.first { it.tag == "ru" }.flag).isEqualTo("🇷🇺")
        assertThat(options.first { it.tag == "pt-BR" }.flag).isEqualTo("🇧🇷")
        assertThat(options.first { it.tag == "eo" }.flag).isNull()
    }
}
