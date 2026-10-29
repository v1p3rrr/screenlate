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

    @Test
    fun `only flags shown more than once in the bubble menu carry codes`() {
        val india = Flags.drawable("in")
        val japan = Flags.drawable("jp")
        val usa = Flags.drawable("us")
        assertThat(Flags.repeated(listOf(india, japan))).isEmpty()
        assertThat(Flags.repeated(listOf(india, japan, india))).containsExactly(india)
        assertThat(Flags.repeated(listOf(usa, india, usa, india, usa, japan))).containsExactly(usa, india)
    }

    @Test
    fun `hindi and marathi share india's flag, so both carry codes`() {
        val quick = listOf(Language.HINDI, Language.MARATHI, Language.JAPANESE)
        val state = LanguageProfilesState(quick, Language.HINDI)
        val flags = quick.map { Flags.of(state, it) }
        assertThat(Flags.repeated(flags)).containsExactly(Flags.drawable("in"))
        assertThat(Flags.repeated(flags.take(1) + flags.drop(2))).isEmpty()
    }

    @Test
    fun `tamil shares india's flag with hindi and marathi`() {
        val quick = listOf(Language.HINDI, Language.MARATHI, Language.TAMIL)
        val state = LanguageProfilesState(quick, Language.TAMIL)
        val flags = quick.map { Flags.of(state, it) }
        assertThat(flags.distinct()).containsExactly(Flags.drawable("in"))
        assertThat(Flags.repeated(flags)).containsExactly(Flags.drawable("in"))
    }

    @Test
    fun `codes standing in for flags on e-ink are not counted`() {
        assertThat(Flags.repeated(listOf(null, null, Flags.drawable("jp")))).isEmpty()
    }
}
