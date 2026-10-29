package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VietnameseSupportTest {

    @Test
    fun `the lookup starts up to two syllables before the aimed one`() {
        assertThat(VietnameseSupport.earlierStartOffset("Tôi là học ")).isEqualTo("là học ".length)
        assertThat(VietnameseSupport.earlierStartOffset("học  ")).isEqualTo("học  ".length)
        assertThat(VietnameseSupport.earlierStartOffset("Xin chào, học ")).isEqualTo("học ".length)
        assertThat(VietnameseSupport.earlierStartOffset("Xin chào, ")).isEqualTo(0)
        assertThat(VietnameseSupport.earlierStartOffset("")).isEqualTo(0)
    }

    @Test
    fun `other languages start only at the aimed word`() {
        assertThat(EnglishSupport.earlierStartOffset("look ")).isEqualTo(0)
    }

    @Test
    fun `titles and address abbreviations do not end a sentence`() {
        assertThat(VietnameseSupport.endsSentence("ở TP. Hồ Chí Minh", 4)).isFalse()
        assertThat(VietnameseSupport.endsSentence("GS. Nguyễn", 2)).isFalse()
        assertThat(VietnameseSupport.endsSentence("Trời mưa. Tôi", 8)).isTrue()
    }

    @Test
    fun `classifier forms are left out and fonts need the vietnamese subset`() {
        assertThat(VietnameseSupport.ignoredFormTags).containsExactly("classifier")
        assertThat(VietnameseSupport.fontSubsets).containsExactly("latin", "vietnamese")
        assertThat(VietnameseSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
    }

    @Test
    fun `central, northern and southern alone name the regions of vietnam`() {
        assertThat(VietnameseSupport.formTagNames).containsExactly(
            "central", "central-vietnam",
            "northern", "northern-vietnam",
            "southern", "southern-vietnam",
        )
        assertThat(ThaiSupport.formTagNames).isEmpty()
    }
}
