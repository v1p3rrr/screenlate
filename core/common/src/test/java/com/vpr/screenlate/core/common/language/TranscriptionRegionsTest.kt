package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptionRegionsTest {
    private fun codePoints(vararg points: Int): String = points.joinToString("") { String(Character.toChars(it)) }

    /** 🇺🇸 for `us`. */
    private fun flag(country: String): String = codePoints(*country.map { 0x1F1E6 + (it - 'a') }.toIntArray())

    /** 🏴 with the tag sequence of [subdivision] (`gbeng` for England). */
    private fun subdivisionFlag(subdivision: String): String =
        codePoints(0x1F3F4, *subdivision.map { 0xE0000 + it.code }.toIntArray(), 0xE007F)

    @Test
    fun `a flag names its country`() {
        assertThat(TranscriptionRegions.ofFlag(flag("us"))).isEqualTo("us")
        assertThat(TranscriptionRegions.ofFlag(flag("gb"))).isEqualTo("gb")
        assertThat(TranscriptionRegions.ofFlag(flag("nz"))).isEqualTo("nz")
    }

    @Test
    fun `a subdivision flag counts as its country`() {
        assertThat(TranscriptionRegions.ofFlag(subdivisionFlag("gbeng"))).isEqualTo("gb")
        assertThat(TranscriptionRegions.ofFlag(subdivisionFlag("gbsct"))).isEqualTo("gb")
        assertThat(TranscriptionRegions.ofFlag(subdivisionFlag("usca"))).isEqualTo("us")
    }

    @Test
    fun `anything else is no flag`() {
        assertThat(TranscriptionRegions.ofFlag("us")).isNull()
        assertThat(TranscriptionRegions.ofFlag("General-American")).isNull()
        // "From German": a flag with an arrow after it.
        assertThat(TranscriptionRegions.ofFlag(flag("de") + codePoints(0x2B05, 0xFE0F))).isNull()
        assertThat(TranscriptionRegions.ofFlag(codePoints(0x1F1FA))).isNull()
        assertThat(TranscriptionRegions.ofFlag(codePoints(0x1F3F4))).isNull()
        assertThat(TranscriptionRegions.ofFlag(codePoints(0x1F3F4, 0xE0067, 0xE0062, 0xE0065))).isNull()
    }

    @Test
    fun `flags and accent names give each region once, in tag order`() {
        val names = EnglishSupport.transcriptionRegionNames
        val tags = listOf("General-American", flag("us"), "dialect", subdivisionFlag("gbeng"), flag("gb"), "British")

        assertThat(TranscriptionRegions.of(tags, names)).containsExactly("us", "gb").inOrder()
    }

    @Test
    fun `grammar tags that look like country codes are no regions`() {
        assertThat(TranscriptionRegions.of(listOf("sg", "pl", "ccm"), EnglishSupport.transcriptionRegionNames)).isEmpty()
        assertThat(TranscriptionRegions.of(listOf("Singapore"), EnglishSupport.transcriptionRegionNames)).containsExactly("sg")
    }

    @Test
    fun `languages without accent names read flags only`() {
        assertThat(TranscriptionRegions.of(listOf("General-American", flag("ca")), JapaneseSupport.transcriptionRegionNames))
            .containsExactly("ca")
    }
}
