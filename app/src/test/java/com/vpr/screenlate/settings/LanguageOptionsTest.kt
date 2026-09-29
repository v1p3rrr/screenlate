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

    @Test
    fun `chinese scripts and brazilian portuguese`() {
        val options = LanguageOptions.of(listOf("zh-Hans", "zh-Hant", "pt").map(Locale::forLanguageTag))
        assertThat(options.first { it.tag == "zh-Hans" }.flag).isEqualTo("🇨🇳")
        assertThat(options.first { it.tag == "zh-Hant" }.flag).isEqualTo("🇨🇳")
        assertThat(options.first { it.tag == "pt" }.flag).isEqualTo("🇧🇷")
        assertThat(options.map { it.name }.toSet()).hasSize(3)
    }

    @Test
    fun `the current locale matches by language and script`() {
        val options = LanguageOptions.of(listOf("en", "pt", "zh-Hans", "zh-Hant").map(Locale::forLanguageTag))
        fun selected(tag: String) = LanguageOptions.selected(options, tag)?.tag

        assertThat(selected("pt")).isEqualTo("pt")
        assertThat(selected("pt-BR")).isEqualTo("pt")
        assertThat(selected("en-US")).isEqualTo("en")
        assertThat(selected("zh-Hans-CN")).isEqualTo("zh-Hans")
        assertThat(selected("zh-CN")).isEqualTo("zh-Hans")
        assertThat(selected("zh-TW")).isEqualTo("zh-Hant")
        assertThat(selected("zh-Hant-HK")).isEqualTo("zh-Hant")
        assertThat(selected("de")).isNull()
        assertThat(selected("")).isNull()
    }
}
