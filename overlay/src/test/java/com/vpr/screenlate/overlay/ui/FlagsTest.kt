package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import java.util.Locale
import org.junit.Test

class FlagsTest {
    @Test
    fun `every flag country of every language has a flag`() {
        for (language in Language.entries) {
            language.support.flagCountries.forEach { assertThat(Flags.drawable(it)).isNotNull() }
        }
    }

    @Test
    fun `a language shows its chosen flag, otherwise the default one`() {
        val state = LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH)
        assertThat(Flags.of(state, Language.ENGLISH)).isEqualTo(Flags.drawable("us"))
        assertThat(Flags.of(state.copy(flags = mapOf(Language.ENGLISH to "gb")), Language.ENGLISH)).isEqualTo(Flags.drawable("gb"))
    }

    @Test
    fun `e-ink shows the language code and countries have names`() {
        assertThat(Flags.code(Language.JAPANESE)).isEqualTo("JA")
        assertThat(Flags.countryName("gb", Locale.ENGLISH)).isEqualTo("United Kingdom")
    }

    @Test
    fun `a language's own flag is named by the language`() {
        assertThat(Flags.drawable("lang_la")).isNotNull()
        assertThat(Flags.countryName("lang_la", Locale.ENGLISH)).isEqualTo("Latin")
        assertThat(Flags.drawable("lang_eo")).isNotNull()
        assertThat(Flags.countryName("lang_eo", Locale.ENGLISH)).isEqualTo("Esperanto")
    }

    @Test
    fun `a region's flag has a drawable and its code for a name`() {
        assertThat(Flags.drawable("gb-wls")).isNotNull()
        assertThat(Flags.countryName("gb-wls", Locale.ENGLISH)).isEqualTo("GB-WLS")
    }
}
