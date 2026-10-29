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
            if (font.languages.isNotEmpty()) {
                font.languages.forEach { assertThat(Language.of(it)).isNotNull() }
                assertThat(font.languages).containsNoDuplicates()
            } else {
                assertThat(font.subsets).isNotEmpty()
                assertThat(font.letters.orEmpty()).matches("[0-9A-F]{4}(-[0-9A-F]{4})?(,[0-9A-F]{4}(-[0-9A-F]{4})?)*")
            }
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
    fun `kazakh and kyrgyz take the cyrillic fonts that have the extended letters`() {
        for (language in listOf(Language.KAZAKH, Language.KYRGYZ)) {
            assertThat(fonts.filter { it.isOfferedFor(language) }.map { it.family })
                .containsExactly("Literata", "Inter", "PT Serif", "PT Sans", "Montserrat", "Commissioner")
        }
    }

    @Test
    fun `bulgarian takes sofia sans of its own and every cyrillic font, PT without its grave i`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.BULGARIAN) }.map { it.family }).containsExactly(
            "Sofia Sans", "Literata", "Inter", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
        )
        // Sofia Sans draws Bulgarian forms under every language and has no Mongolian ө ү.
        val sofia = fonts.single { it.id == "sofia-sans" }
        assertThat(Language.entries.filter { sofia.isOfferedFor(it) }).containsExactly(Language.BULGARIAN)
        // PT stays offered without ѝ and ѐ, which the phone's font draws (Literata composes ѐ from е and the grave).
        assertThat(fonts.single { it.id == "pt-serif" }.hasLetters("ѝ")).isFalse()
        assertThat(fonts.single { it.id == "literata" }.hasLetters("ѝ")).isTrue()
        assertThat(fonts.single { it.id == "montserrat" }.hasLetters("ѝѐ")).isTrue()
    }

    @Test
    fun `macedonian and mongolian take every cyrillic font`() {
        for (language in listOf(Language.MACEDONIAN, Language.MONGOLIAN)) {
            assertThat(fonts.filter { it.isOfferedFor(language) }.map { it.family })
                .containsExactly("Literata", "Inter", "PT Serif", "PT Sans", "Montserrat", "Commissioner")
        }
    }

    @Test
    fun `swedish takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.SWEDISH) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `norwegian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.NORWEGIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `danish takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.DANISH) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `romanian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.ROMANIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `lithuanian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.LITHUANIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
        assertThat(fonts.filter { it.languages.isEmpty() && it.subsets.contains("latin-ext") }.all { it.hasLetters("ąčęėįšųūž") })
            .isTrue()
    }

    @Test
    fun `slovak takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.SLOVAK) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
        assertThat(fonts.filter { it.languages.isEmpty() && it.subsets.contains("latin-ext") }.all { it.hasLetters("áäčďéíĺľňóôŕšťúýž") })
            .isTrue()
    }

    @Test
    fun `slovenian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.SLOVENIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `albanian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.ALBANIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `swahili takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.SWAHILI) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `welsh takes the latin fonts that have its circumflex w and y`() {
        // PT Serif and PT Sans declare Latin Extended but have no ŵ or ŷ.
        assertThat(fonts.filter { it.isOfferedFor(Language.WELSH) }.map { it.family })
            .containsExactly("Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "Montserrat", "Commissioner")
    }

    @Test
    fun `irish takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.IRISH) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `scottish gaelic takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.SCOTTISH_GAELIC) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `finnish takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.FINNISH) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `estonian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.ESTONIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `azerbaijani takes the latin fonts with schwa`() {
        // PT Serif, PT Sans and Atkinson Hyperlegible Next lack ə and Ə.
        assertThat(fonts.filter { it.isOfferedFor(Language.AZERBAIJANI) }.map { it.family })
            .containsExactly("Literata", "Inter", "Lexend", "Montserrat", "Commissioner")
    }

    @Test
    fun `uzbek takes the latin fonts with its turned comma and apostrophe`() {
        // PT Serif, PT Sans and Atkinson Hyperlegible Next lack ʻ and ʼ.
        assertThat(fonts.filter { it.isOfferedFor(Language.UZBEK) }.map { it.family })
            .containsExactly("Literata", "Inter", "Lexend", "Montserrat", "Commissioner")
    }

    @Test
    fun `hungarian takes every latin font`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.HUNGARIAN) }.map { it.family })
            .containsExactly(
                "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
            )
    }

    @Test
    fun `latin takes the latin fonts that have its long vowels`() {
        // Atkinson Hyperlegible Next declares Latin Extended but has no ō.
        assertThat(fonts.filter { it.isOfferedFor(Language.LATIN) }.map { it.family })
            .containsExactly("Literata", "Inter", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner")
    }

    @Test
    fun `esperanto takes the latin fonts that have its hat letters`() {
        // Atkinson Hyperlegible Next declares Latin Extended but has no ĉ.
        assertThat(fonts.filter { it.isOfferedFor(Language.ESPERANTO) }.map { it.family })
            .containsExactly("Literata", "Inter", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner")
    }

    @Test
    fun `a font offered by its subsets is offered for every language whose letters it has`() {
        val atkinson = fonts.single { it.id == "atkinson-hyperlegible-next" }
        assertThat(atkinson.hasLetters("āēīūæœ")).isTrue()
        assertThat(atkinson.hasLetters("ō")).isFalse()
        assertThat(atkinson.hasLetters("ĉ")).isFalse()
        assertThat(fonts.single { it.id == "inter" }.hasLetters("ĉĝĥĵŝŭāēīōū")).isTrue()
        assertThat(fonts.single { it.id == "pt-serif" }.hasLetters("ŵŷ")).isFalse()
        // Each language with subsets keeps every font that has them, but for Latin's and Esperanto's Atkinson, Welsh's
        // PT fonts and the three without Azerbaijani ə or Uzbek ʻ.
        val lacking = mapOf(
            Language.LATIN to setOf("atkinson-hyperlegible-next"),
            Language.ESPERANTO to setOf("atkinson-hyperlegible-next"),
            Language.WELSH to setOf("pt-serif", "pt-sans"),
            Language.AZERBAIJANI to setOf("atkinson-hyperlegible-next", "pt-serif", "pt-sans"),
            Language.UZBEK to setOf("atkinson-hyperlegible-next", "pt-serif", "pt-sans"),
        )
        for (language in Language.entries.filter { it.support.fontSubsets.isNotEmpty() }) {
            val bySubsets = fonts.filter {
                it.languages.isEmpty() && it.subsets.containsAll(language.support.fontSubsets) && it.id !in lacking[language].orEmpty()
            }
            assertThat(fonts.filter { it.languages.isEmpty() && it.isOfferedFor(language) }).containsExactlyElementsIn(bySubsets)
        }
    }

    @Test
    fun `chinese has fonts for both scripts`() {
        val chinese = fonts.filter { Language.CHINESE.code in it.languages }.map { it.family }
        assertThat(chinese).containsExactly("Noto Sans SC", "Noto Serif SC", "Noto Sans TC", "Noto Serif TC", "LXGW WenKai TC")
    }

    @Test
    fun `korean has gothic and myeongjo fonts`() {
        val korean = fonts.filter { Language.KOREAN.code in it.languages }.map { it.family }
        assertThat(korean).containsExactly("Noto Sans KR", "Noto Serif KR", "Nanum Gothic", "Nanum Myeongjo")
    }

    @Test
    fun `hindi has two sans-serifs and a serif, mukta offered for marathi as well`() {
        val hindi = fonts.filter { Language.HINDI.code in it.languages }
        assertThat(hindi.map { it.family }).containsExactly("Mukta", "Hind", "Tiro Devanagari Hindi")
        for (font in hindi) {
            val offered = if (font.id == "mukta") listOf(Language.HINDI, Language.MARATHI) else listOf(Language.HINDI)
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactlyElementsIn(offered)
        }
        // Latin fonts have no Devanagari.
        assertThat(fonts.filter { it.isOfferedFor(Language.HINDI) }).containsExactlyElementsIn(hindi)
    }

    @Test
    fun `marathi has hindi's mukta and a serif of its own`() {
        val marathi = fonts.filter { it.isOfferedFor(Language.MARATHI) }
        assertThat(marathi.map { it.family }).containsExactly("Mukta", "Tiro Devanagari Marathi")
        val tiro = fonts.single { it.id == "tiro-devanagari-marathi" }
        assertThat(Language.entries.filter { tiro.isOfferedFor(it) }).containsExactly(Language.MARATHI)
    }

    @Test
    fun `bengali has a sans-serif and a serif, offered for bengali only`() {
        val bengali = fonts.filter { Language.BENGALI.code in it.languages }
        assertThat(bengali.map { it.family }).containsExactly("Hind Siliguri", "Tiro Bangla")
        for (font in bengali) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.BENGALI)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.BENGALI) }).containsExactlyElementsIn(bengali)
    }

    @Test
    fun `tamil has two sans-serifs and a serif, offered for tamil only`() {
        val tamil = fonts.filter { Language.TAMIL.code in it.languages }
        assertThat(tamil.map { it.family }).containsExactly("Hind Madurai", "Mukta Malar", "Tiro Tamil")
        for (font in tamil) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.TAMIL)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.TAMIL) }).containsExactlyElementsIn(tamil)
    }

    @Test
    fun `telugu has a sans-serif and a serif, offered for telugu only`() {
        val telugu = fonts.filter { Language.TELUGU.code in it.languages }
        assertThat(telugu.map { it.family }).containsExactly("Anek Telugu", "Tiro Telugu")
        for (font in telugu) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.TELUGU)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.TELUGU) }).containsExactlyElementsIn(telugu)
    }

    @Test
    fun `thai has looped and loopless fonts, offered for thai only`() {
        val thai = fonts.filter { Language.THAI.code in it.languages }
        assertThat(thai.map { it.family }).containsExactly("Sarabun", "Noto Sans Thai Looped", "Kanit", "Prompt")
        for (font in thai) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.THAI)
        }
        // Latin fonts have no Thai.
        assertThat(fonts.filter { it.isOfferedFor(Language.THAI) }).containsExactlyElementsIn(thai)
    }

    @Test
    fun `khmer has a sans-serif and a serif, offered for khmer only`() {
        val khmer = fonts.filter { Language.KHMER.code in it.languages }
        assertThat(khmer.map { it.family }).containsExactly("Kantumruy Pro", "Hanuman")
        for (font in khmer) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.KHMER)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.KHMER) }).containsExactlyElementsIn(khmer)
    }

    @Test
    fun `the arabic script has two sans-serifs and a naskh, offered for arabic and persian and not for latin languages`() {
        val arabic = fonts.filter { it.subsets == listOf("arabic") }
        assertThat(arabic.map { it.family }).containsExactly("Noto Sans Arabic", "Amiri", "Vazirmatn")
        // Each has the letters of both languages (پ چ ژ گ ک ی, ة ى and both digit sets).
        for (font in arabic) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.ARABIC, Language.PERSIAN)
        }
        assertThat(fonts.filter { it.isOfferedFor(Language.ARABIC) }).containsExactlyElementsIn(arabic)
        assertThat(fonts.filter { it.isOfferedFor(Language.PERSIAN) }).containsExactlyElementsIn(arabic)
    }

    @Test
    fun `hebrew has a sans-serif and a serif, offered for hebrew only`() {
        val hebrew = fonts.filter { it.subsets == listOf("hebrew") }
        assertThat(hebrew.map { it.family }).containsExactly("Heebo", "Frank Ruhl Libre")
        for (font in hebrew) {
            assertThat(Language.entries.filter { font.isOfferedFor(it) }).containsExactly(Language.HEBREW)
        }
        // Latin fonts have no Hebrew.
        assertThat(fonts.filter { it.isOfferedFor(Language.HEBREW) }).containsExactlyElementsIn(hebrew)
    }

    @Test
    fun `georgian and armenian are offered no downloadable font, only the system's`() {
        assertThat(fonts.filter { it.isOfferedFor(Language.GEORGIAN) }).isEmpty()
        assertThat(fonts.filter { it.isOfferedFor(Language.ARMENIAN) }).isEmpty()
    }

    @Test
    fun `latin fonts are offered for every language whose letters they have`() {
        val latin = Language.entries.filter { it.support.fontSubsets == setOf("latin") }
        assertThat(latin).containsAtLeast(Language.ENGLISH, Language.SPANISH, Language.FRENCH, Language.GERMAN)
        val families = listOf(
            "Literata", "Inter", "Atkinson Hyperlegible Next", "Lexend", "PT Serif", "PT Sans", "Montserrat", "Commissioner",
        )
        for (family in families) {
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
