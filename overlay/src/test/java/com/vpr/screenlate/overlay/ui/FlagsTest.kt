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
    fun `the five languages with india's flag carry codes, the others none`() {
        val codes = FlagCodes()
        val india = listOf(Language.HINDI, Language.MARATHI, Language.TAMIL, Language.TELUGU, Language.KANNADA)
        india.forEach { assertThat(codes.coded(it)).isTrue() }
        assertThat(Language.entries.filter { codes.coded(it) }).containsExactlyElementsIn(india)
        assertThat(india.map(Flags::code)).containsExactly("HI", "MR", "TA", "TE", "KN").inOrder()
    }

    @Test
    fun `a language carries its code whether or not the others with its flag are turned on`() {
        val alone = FlagCodes(LanguageProfilesState(listOf(Language.HINDI, Language.JAPANESE), Language.HINDI))
        assertThat(alone.coded(Language.HINDI)).isTrue()
        assertThat(alone.coded(Language.JAPANESE)).isFalse()
        val nothingOn = FlagCodes(LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE))
        assertThat(nothingOn.coded(Language.TAMIL)).isTrue()
    }

    @Test
    fun `cantonese has hong kong's flag, apart from chinese, so neither carries a code`() {
        val state = LanguageProfilesState(listOf(Language.CHINESE, Language.CANTONESE), Language.CANTONESE)
        assertThat(Flags.of(state, Language.CANTONESE)).isEqualTo(Flags.drawable("hk"))
        assertThat(Flags.drawable("hk")).isNotNull()
        val codes = FlagCodes(state)
        assertThat(codes.coded(Language.CANTONESE)).isFalse()
        assertThat(codes.coded(Language.CHINESE)).isFalse()
    }

    @Test
    fun `either english flag stands alone`() {
        val state = LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH)
        assertThat(FlagCodes(state).coded(Language.ENGLISH)).isFalse()
        assertThat(FlagCodes(state.copy(flags = mapOf(Language.ENGLISH to "gb"))).coded(Language.ENGLISH)).isFalse()
    }

    @Test
    fun `the user's flag choice decides which flag a language shows`() {
        val state = LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH)
        val british = FlagCodes(state.copy(flags = mapOf(Language.ENGLISH to "gb")))
        // Another language's flag of the United Kingdom would be English's too then, and the United States' no longer.
        assertThat(british.coded(Language.WELSH, "gb")).isTrue()
        assertThat(british.coded(Language.WELSH, "us")).isFalse()
        assertThat(FlagCodes(state).coded(Language.WELSH, "gb")).isFalse()
        assertThat(FlagCodes(state).coded(Language.WELSH, "us")).isTrue()
        // A choice outside a language's flags is ignored, as everywhere.
        assertThat(FlagCodes(state.copy(flags = mapOf(Language.TAMIL to "lk"))).coded(Language.TAMIL)).isTrue()
    }

    @Test
    fun `a flag to choose carries the code when another language shows it`() {
        val codes = FlagCodes()
        assertThat(codes.coded(Language.ENGLISH, "gb")).isFalse()
        assertThat(codes.coded(Language.ENGLISH, "in")).isTrue()
        assertThat(codes.coded(Language.PORTUGUESE, "pt")).isFalse()
        assertThat(codes.coded(Language.HINDI, "in")).isTrue()
    }

    @Test
    fun `serbian's code is written in cyrillic letters`() {
        assertThat(Flags.code(Language.SERBIAN)).isEqualTo("СР")
        assertThat(Flags.code(Language.CROATIAN)).isEqualTo("HR")
    }

    @Test
    fun `flag codes of the same choices are equal`() {
        val state = LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH)
        // The languages turned on do not matter, only the flag choices.
        assertThat(FlagCodes(state)).isEqualTo(FlagCodes(LanguageProfilesState(listOf(Language.HINDI, Language.TAMIL), Language.TAMIL)))
        assertThat(FlagCodes(state)).isEqualTo(FlagCodes())
        assertThat(FlagCodes(state.copy(flags = mapOf(Language.ENGLISH to "gb")))).isNotEqualTo(FlagCodes(state))
    }
}
