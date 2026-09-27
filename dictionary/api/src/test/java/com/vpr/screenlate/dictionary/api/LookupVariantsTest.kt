package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import org.junit.Test

class LookupVariantsTest {

    private fun variants(text: String, settings: LookupSettings) =
        LookupVariants.of(text, settings, JapaneseSupport).map { it.text }

    @Test
    fun `only the original text when no rule applies`() {
        assertThat(variants("食べる", LookupSettings())).containsExactly("食べる")
    }

    @Test
    fun `the spelling variant follows the original`() {
        assertThat(variants("1人で", LookupSettings())).containsExactly("1人で", "一人で").inOrder()
    }

    @Test
    fun `romaji applies to the original and the spelling variant`() {
        assertThat(variants("ta be ru", LookupSettings(romaji = true)))
            .containsExactly("ta be ru", "た べ る", "taberu", "たべる").inOrder()
    }

    @Test
    fun `romaji adds a kana text`() {
        assertThat(variants("taberu", LookupSettings(romaji = true))).containsExactly("taberu", "たべる").inOrder()
        assertThat(variants("食べる", LookupSettings(romaji = true))).containsExactly("食べる")
    }

    @Test
    fun `results of a variant point at the source characters`() {
        val variant = JapaneseSupport.fromLatin(MappedText.identity("taberu yo"))!!
        val result = LookupResult(matched = "たべる", deinflected = "たべる", term = TermEntry("食べる", "たべる"))
        val mapped = result.inSource("taberu yo", variant)
        assertThat(mapped.matched).isEqualTo("taberu")
        assertThat(mapped.preprocessorSteps).isEqualTo(1)
        assertThat(result.inSource("たべる", MappedText.identity("たべる"))).isSameInstanceAs(result)
    }
}
