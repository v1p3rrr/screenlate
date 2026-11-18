package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.EnglishSupport
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

    @Test
    fun `a word hyphenated at a line end is looked up joined and with its hyphen after the text as written`() {
        val text = "impor- tant thing"
        val found = LookupVariants.of(text, LookupSettings(), EnglishSupport)
        assertThat(found.map { it.text }).containsExactly(text, "important thing", "impor-tant thing").inOrder()
        val result = LookupResult(matched = "important", deinflected = "important", term = TermEntry("important", ""))
        val mapped = result.inSource(text, found[1])
        assertThat(mapped.matched).isEqualTo("impor- tant")
        assertThat(mapped.variantMatched).isEqualTo("important")
    }

    @Test
    fun `an uppercase word hyphenated at a line end is looked up joined and with its hyphen`() {
        val text = "ЗАКОН- ЧИЛИ, ДА?"
        val found = LookupVariants.of(text, LookupSettings(), com.vpr.screenlate.core.common.language.RussianSupport)
        assertThat(found.map { it.text }).containsExactly(text, "ЗАКОНЧИЛИ, ДА?", "ЗАКОН-ЧИЛИ, ДА?").inOrder()
        val result = LookupResult(matched = "ЗАКОНЧИЛИ", deinflected = "закончить", term = TermEntry("закончить", ""))
        val mapped = result.inSource(text, found[1])
        assertThat(mapped.matched).isEqualTo("ЗАКОН- ЧИЛИ")
        assertThat(mapped.variantMatched).isEqualTo("ЗАКОНЧИЛИ")
    }

    @Test
    fun `custom text replacements add a mapped variant and safely ignore invalid or disabled rules`() {
        val rules = listOf(
            TextReplacement(pattern = "★", replacement = "", regex = false, enabled = true),
            TextReplacement(pattern = "[invalid(", replacement = "x", regex = true, enabled = true),
            TextReplacement(pattern = "猫", replacement = "犬", regex = false, enabled = false),
            TextReplacement(pattern = "([あ-ん])ー", replacement = "$1", regex = true, enabled = true),
        )
        val found = LookupVariants.of("猫★すーき", LookupSettings(textReplacements = rules), JapaneseSupport)
        assertThat(found.map { it.text }).containsExactly("猫★すーき", "猫すき").inOrder()
        val mapped = LookupResult(matched = "猫すき", deinflected = "猫すき", term = TermEntry("猫好き", "ねこすき"))
            .inSource("猫★すーき", found[1])
        assertThat(mapped.matched).isEqualTo("猫★すーき")
        assertThat(mapped.preprocessorSteps).isEqualTo(1)
    }

    @Test
    fun `literal replacement treats dollar signs as literal text and supports ignoreCase`() {
        val rules = listOf(
            TextReplacement(pattern = "USD", replacement = "$1", regex = false, ignoreCase = true, enabled = true),
        )
        val found = LookupVariants.of("usd", LookupSettings(textReplacements = rules), EnglishSupport)
        assertThat(found.map { it.text }).containsExactly("usd", "$1").inOrder()
    }
}
