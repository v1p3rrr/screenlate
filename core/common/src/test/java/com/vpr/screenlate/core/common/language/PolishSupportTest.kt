package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = PolishSupport.endsSentence(text, at)

    @Test
    fun `low quotes close with the high one`() {
        assertThat(PolishSupport.quotePairs).containsEntry('„', '”')
        assertThat(PolishSupport.quotePairs).containsEntry('«', '»')
    }

    @Test
    fun `ordinals and abbreviations do not end a sentence`() {
        assertThat(ends("To jest 5. rocznica")).isFalse()
        assertThat(ends("Owoce, np. jabłka")).isFalse()
        assertThat(ends("W XX w. powstało")).isFalse()
        assertThat(ends("W 2020 r. otwarto", "W 2020 r".length)).isFalse()
        assertThat(ends("Pada deszcz. Jutro")).isTrue()
        assertThat(ends("Urodził się w 1990. Potem")).isTrue()
    }

    @Test
    fun `ok holds the sentence only before a number or a lowercase word`() {
        assertThat(ends("Było ok. 20 osób")).isFalse()
        assertThat(ends("Trwało to ok. godziny")).isFalse()
        assertThat(ends("Jest ok. Idziemy")).isTrue()
        assertThat(ends("Wszystko ok.")).isTrue()
    }

    @Test
    fun `wiktionary comes first among recordings`() {
        assertThat(PolishSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(PolishSupport.audioRegions).isEmpty()
    }

    @Test
    fun `fonts need the extended latin letters`() {
        assertThat(PolishSupport.fontSubsets).containsExactly("latin", "latin-ext")
    }
}
