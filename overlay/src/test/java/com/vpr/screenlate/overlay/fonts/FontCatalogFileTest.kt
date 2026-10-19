package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Test

/** The bundled font catalog: a broken file hides every font. */
class FontCatalogFileTest {

    private val fonts = Json { ignoreUnknownKeys = true }
        .decodeFromString<FontCatalog>(File("src/main/assets/fonts/catalog.json").readText()).fonts

    /** The interface locales, as keys of catalog texts. */
    private val locales = listOf("en", "ru", "de", "es", "fr", "it", "ja", "ko", "pl", "pt", "tr", "vi", "zh", "zh-Hant")

    @Test
    fun `every font is complete and unique`() {
        assertThat(fonts.map { it.id }).containsNoDuplicates()
        assertThat(fonts.map { it.family }).containsNoDuplicates()
        for (font in fonts) {
            val language = font.language
            if (language != null) assertThat(Language.of(language)).isNotNull() else assertThat(font.subsets).isNotEmpty()
            assertThat(font.description.keys).containsExactlyElementsIn(locales)
            assertThat(font.files).isNotEmpty()
            for (file in font.files) {
                assertThat(file.url).startsWith("https://")
                assertThat(file.weight).matches("[1-9]00( [1-9]00)?")
            }
            assertThat(font.sizeMb).isGreaterThan(0.0)
            assertThat(font.license).isNotEmpty()
            assertThat(font.homepage).startsWith("https://")
        }
    }

    @Test
    fun `chinese has fonts for both scripts`() {
        val chinese = fonts.filter { it.language == Language.CHINESE.code }.map { it.family }
        assertThat(chinese).containsExactly("Noto Sans SC", "Noto Serif SC", "Noto Sans TC", "Noto Serif TC", "LXGW WenKai TC")
    }

    @Test
    fun `korean has gothic and myeongjo fonts`() {
        val korean = fonts.filter { it.language == Language.KOREAN.code }.map { it.family }
        assertThat(korean).containsExactly("Noto Sans KR", "Noto Serif KR", "Nanum Gothic", "Nanum Myeongjo")
    }

    @Test
    fun `hindi has two sans-serifs and a serif, offered for hindi only`() {
        val hindi = fonts.filter { it.language == Language.HINDI.code }
        assertThat(hindi.map { it.family }).containsExactly("Mukta", "Hind", "Tiro Devanagari Hindi")
        for (font in hindi) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.HINDI)
        }
        // Latin fonts have no Devanagari.
        assertThat(fonts.filter { it.isOfferedFor(Language.HINDI) }).containsExactlyElementsIn(hindi)
    }

    @Test
    fun `bengali has a sans-serif and a serif, offered for bengali only`() {
        val bengali = fonts.filter { it.language == Language.BENGALI.code }
        assertThat(bengali.map { it.family }).containsExactly("Hind Siliguri", "Tiro Bangla")
        for (font in bengali) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.BENGALI)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.BENGALI) }).containsExactlyElementsIn(bengali)
    }

    @Test
    fun `latin fonts are offered for every language whose letters they have`() {
        val latin = Language.entries.filter { it.support.fontSubsets == setOf("latin") }
        assertThat(latin).containsAtLeast(Language.ENGLISH, Language.SPANISH, Language.FRENCH, Language.GERMAN)
        for (family in listOf("Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans")) {
            val font = fonts.first { it.family == family }
            assertThat(latin.filter { font.isOfferedFor(it) }).containsExactlyElementsIn(latin)
            assertThat(font.isOfferedFor(Language.JAPANESE)).isFalse()
            assertThat(font.isOfferedFor(Language.KOREAN)).isFalse()
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.CHINESE) }.map { it.family }).doesNotContain("Inter")
        assertThat(fonts.first { it.family == "Noto Sans JP" }.isOfferedFor(Language.ENGLISH)).isFalse()
    }

    @Test
    fun `the description follows the interface locale, traditional chinese included`() {
        val font = fonts.first { it.id == "noto-sans-tc" }
        assertThat(font.description(Locale.forLanguageTag("zh-TW"))).isEqualTo(font.description["zh-Hant"])
        assertThat(font.description(Locale.forLanguageTag("zh-Hans-CN"))).isEqualTo(font.description["zh"])
        assertThat(font.description(Locale.forLanguageTag("ru"))).isEqualTo(font.description["ru"])
        assertThat(font.description(Locale.forLanguageTag("eo"))).isEqualTo(font.description["en"])
    }
}
