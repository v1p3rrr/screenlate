package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegionChipsTest {
    @Test
    fun `a flag with directions names its country and the directions`() {
        assertThat(RegionChips.parse("🇧🇷🎯")).isEqualTo("br" to listOf("central"))
        assertThat(RegionChips.parse("🇩🇪⬅️")).isEqualTo("de" to listOf("western"))
        assertThat(RegionChips.parse("🇩🇪🎯⬆️")).isEqualTo("de" to listOf("central", "northern"))
    }

    @Test
    fun `a flag alone, a flag with a place and other tags are not region chips`() {
        assertThat(RegionChips.parse("🇺🇸")).isNull()
        assertThat(RegionChips.parse("🇧🇷SP")).isNull()
        assertThat(RegionChips.parse("🏴󠁧󠁢󠁳󠁣󠁴󠁿")).isNull()
        assertThat(RegionChips.parse("n")).isNull()
        assertThat(RegionChips.parse("")).isNull()
    }

    @Test
    fun `only the chips of another language's country are relabelled`() {
        val tags = listOf("n", "🇺🇸", "🇧🇷🎯", "🇩🇪⬅️")
        assertThat(RegionChips.foreign(tags, listOf("vn"))).containsExactly("🇧🇷🎯", "central", "🇩🇪⬅️", "western")
        assertThat(RegionChips.foreign(tags, listOf("br", "pt"))).containsExactly("🇩🇪⬅️", "western")
        assertThat(RegionChips.foreign(tags, listOf("de"))).containsExactly("🇧🇷🎯", "central")
    }
}
