package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DutchSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = DutchSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, ordinals and names do`() {
        assertThat(ends("Fruit, bijv. appels")).isFalse()
        assertThat(ends("Dat is d.w.z. goed", at = 11)).isFalse()
        assertThat(ends("Volgens mr. Jansen")).isFalse()
        assertThat(ends("Zie blz. 12")).isFalse()
        assertThat(ends("Op 5 jan. kwam hij", at = 8)).isTrue()
        assertThat(ends("Ik zag Jan. Hij")).isTrue()
        assertThat(ends("Het was de 3. Daarna")).isTrue()
    }

    @Test
    fun `low quotes close with the high closing quote`() {
        assertThat(DutchSupport.quotePairs['„']).isEqualTo('”')
        assertThat(DutchSupport.quotePairs['“']).isEqualTo('”')
    }

    @Test
    fun `apostrophes stay inside words`() {
        assertThat(DutchSupport.wordStartOffset("auto'", "s")).isEqualTo(5)
        assertThat(DutchSupport.wordStartOffset("zo'", "n")).isEqualTo(3)
    }

    @Test
    fun `compounds split at s, en and e, fonts need the extended latin letters`() {
        assertThat(DutchSupport.compoundLinks).containsExactly("s", "en", "e")
        assertThat(DutchSupport.capitalizedNouns).isFalse()
        assertThat(DutchSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(DutchSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
    }
}
