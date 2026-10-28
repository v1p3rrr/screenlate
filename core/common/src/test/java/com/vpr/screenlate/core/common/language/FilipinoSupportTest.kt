package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.languageName
import java.util.Locale
import org.junit.Test

class FilipinoSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = FilipinoSupport.endsSentence(text, at)

    @Test
    fun `titles, saints and numbers do not end a sentence`() {
        assertThat(ends("Kausap ko si G. Santos")).isFalse()
        assertThat(ends("Nakatira siya sa Sta. Cruz")).isFalse()
        assertThat(ends("Batas Republika Blg. 9165")).isFalse()
        assertThat(ends("Kumain siya. Umalis")).isTrue()
        assertThat(ends("Bumili ng prutas, gulay, atbp. Pagkatapos")).isTrue()
    }

    @Test
    fun `the name gives both filipino and tagalog`() {
        assertThat(FilipinoSupport.nameIn(Locale.ENGLISH)).isEqualTo("Filipino (Tagalog)")
        assertThat(Language.FILIPINO.displayName(Locale.forLanguageTag("ru"))).isEqualTo("Филиппинский (тагалог)")
        assertThat(languageName("tl", Locale.ENGLISH)).isEqualTo("Filipino (Tagalog)")
        assertThat(languageName("fil", Locale.ENGLISH)).isEqualTo("Filipino (Tagalog)")
        assertThat(languageName("sv", Locale.ENGLISH)).isEqualTo("Swedish")
    }

    @Test
    fun `android's code fil and the voice's tag`() {
        assertThat(Language.of("fil")).isEqualTo(Language.FILIPINO)
        assertThat(Language.of("tl")).isEqualTo(Language.FILIPINO)
        assertThat(FilipinoSupport.speechTag).isEqualTo("fil")
    }

    @Test
    fun `baybayin spellings are not followed and the bubble shows ng`() {
        assertThat(FilipinoSupport.ignoredFormTags).containsExactly("Baybayin")
        assertThat(FilipinoSupport.glyph).isEqualTo("F")
        assertThat(FilipinoSupport.flagCountries).containsExactly("ph")
        assertThat(FilipinoSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
    }
}
