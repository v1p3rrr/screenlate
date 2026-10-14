package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
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
            assertThat(Language.of(font.language)).isNotNull()
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
    fun `the description follows the interface locale, traditional chinese included`() {
        val font = fonts.first { it.id == "noto-sans-tc" }
        assertThat(font.description(Locale.forLanguageTag("zh-TW"))).isEqualTo(font.description["zh-Hant"])
        assertThat(font.description(Locale.forLanguageTag("zh-Hans-CN"))).isEqualTo(font.description["zh"])
        assertThat(font.description(Locale.forLanguageTag("ru"))).isEqualTo(font.description["ru"])
        assertThat(font.description(Locale.forLanguageTag("eo"))).isEqualTo(font.description["en"])
    }
}
