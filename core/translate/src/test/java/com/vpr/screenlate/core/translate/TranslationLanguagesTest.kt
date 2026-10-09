package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class TranslationLanguagesTest {
    private fun default(tag: String) = TranslationLanguages.defaultFor(Locale.forLanguageTag(tag), Language.JAPANESE).tag

    @Test
    fun `the interface language is the default target`() {
        assertThat(default("ru")).isEqualTo("ru")
        assertThat(default("de-AT")).isEqualTo("de")
        assertThat(default("ko-KR")).isEqualTo("ko")
    }

    @Test
    fun `English replaces the source language and languages no service has`() {
        assertThat(default("ja")).isEqualTo("en")
        assertThat(default("ja-JP")).isEqualTo("en")
        assertThat(default("xx")).isEqualTo("en")
    }

    @Test
    fun `scripts and regions the services tell apart`() {
        assertThat(default("zh-CN")).isEqualTo("zh-Hans")
        assertThat(default("zh")).isEqualTo("zh-Hans")
        assertThat(default("zh-TW")).isEqualTo("zh-Hant")
        assertThat(default("zh-HK")).isEqualTo("zh-Hant")
        assertThat(default("zh-Hant-TW")).isEqualTo("zh-Hant")
        assertThat(default("zh-Hans-HK")).isEqualTo("zh-Hans")
        assertThat(default("pt")).isEqualTo("pt-BR")
        assertThat(default("pt-BR")).isEqualTo("pt-BR")
        assertThat(default("pt-PT")).isEqualTo("pt-PT")
        assertThat(default("sr")).isEqualTo("sr-Cyrl")
        assertThat(default("sr-Latn-RS")).isEqualTo("sr-Latn")
        assertThat(default("fr-CA")).isEqualTo("fr-CA")
        assertThat(default("fr-BE")).isEqualTo("fr")
        assertThat(default("es-MX")).isEqualTo("es")
    }

    @Test
    fun `other codes for the same language`() {
        assertThat(TranslationLanguages.forLocale(Locale("iw"))?.tag).isEqualTo("he")
        assertThat(default("he")).isEqualTo("he")
        assertThat(default("nn")).isEqualTo("nb")
        assertThat(default("no")).isEqualTo("nb")
        assertThat(default("tl")).isEqualTo("fil")
        assertThat(default("fil")).isEqualTo("fil")
    }

    @Test
    fun `each service gets its own code`() {
        val chinese = TranslationLanguages.of("zh-Hans")!!
        assertThat(chinese.microsoft).isEqualTo("zh-Hans")
        assertThat(chinese.google).isEqualTo("zh-CN")
        assertThat(TranslationLanguages.of("he")!!.google).isEqualTo("iw")
        assertThat(TranslationLanguages.of("pt-BR")!!.microsoft).isEqualTo("pt")
        // Microsoft's "ku" is Central Kurdish, Google's is Kurmanji.
        assertThat(TranslationLanguages.of("ckb")!!.microsoft).isEqualTo("ku")
        assertThat(TranslationLanguages.of("kmr")!!.google).isEqualTo("ku")
        assertThat(TranslationLanguages.of("ZH-hant")?.tag).isEqualTo("zh-Hant")
    }

    @Test
    fun `the table has one entry per tag, each with a service`() {
        val all = TranslationLanguages.all
        assertThat(all.map { it.tag.lowercase() }).containsNoDuplicates()
        assertThat(all.filter { it.microsoft == null && it.google == null }).isEmpty()
        assertThat(all.mapNotNull { it.microsoft }).containsNoDuplicates()
        assertThat(all.mapNotNull { it.google }).containsNoDuplicates()
        assertThat(all.size).isGreaterThan(200)
    }

    @Test
    fun `names come from the system, else from the services`() {
        assertThat(TranslationLanguages.of("ru")!!.displayName(Locale.ENGLISH)).isEqualTo("Russian")
        assertThat(TranslationLanguages.of("ru")!!.displayName(Locale.forLanguageTag("ru"))).isEqualTo("Русский")
        assertThat(TranslationLanguages.of("otq")!!.displayName(Locale.ENGLISH)).isEqualTo("Querétaro Otomi")
    }
}
