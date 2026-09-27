package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.TextReplacement
import org.junit.Test

class LookupVariantsTest {

    private fun variants(text: String, settings: LookupSettings) =
        LookupVariants.of(text, settings, JapaneseSupport).map { it.text }

    @Test
    fun `only the original text by default`() {
        assertThat(variants("食べる", LookupSettings())).containsExactly("食べる")
    }

    @Test
    fun `each replacement group adds a text`() {
        val settings = LookupSettings(
            replacementGroups = listOf(
                listOf(TextReplacement("《[^》]*》")),
                listOf(TextReplacement("ｶﾞｯｺｳ", "学校"), TextReplacement("学校", "がっこう")),
                listOf(TextReplacement("[", enabled = true)),
            ),
        )
        assertThat(variants("ｶﾞｯｺｳ《がっこう》", settings))
            .containsExactly("ｶﾞｯｺｳ《がっこう》", "ｶﾞｯｺｳ", "がっこう《がっこう》").inOrder()
    }

    @Test
    fun `the original can be left out`() {
        val settings = LookupSettings(
            searchOriginal = false,
            replacementGroups = listOf(listOf(TextReplacement("x", "y"))),
        )
        assertThat(variants("xa", settings)).containsExactly("ya")
        assertThat(variants("za", settings)).containsExactly("za")
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
