package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class SerboCroatianSupportTest {

    private val latin = SerboCroatianLatinSupport
    private val cyrillic = SerboCroatianCyrillicSupport

    private fun ends(support: LanguageSupport, text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `ordinals, the era and abbreviations do not end a sentence in either script`() {
        for (support in listOf(latin, cyrillic)) {
            assertThat(ends(support, "Dana 5. maja")).isFalse()
            assertThat(ends(support, "Дана 5. маја")).isFalse()
            assertThat(ends(support, "Godine 1918. godine")).isFalse()
            assertThat(ends(support, "Bilo je to 1990. Onda")).isTrue()
        }
        assertThat(ends(latin, "Voće, npr. jabuke")).isFalse()
        assertThat(ends(cyrillic, "Воће, нпр. јабуке")).isFalse()
        assertThat(ends(latin, "u 19. st. se", at = 8)).isFalse()
        assertThat(ends(latin, "Razgovarao je s dr. Petrovićem")).isFalse()
        assertThat(ends(latin, "Rekao je to. Onda")).isTrue()
        assertThat(ends(cyrillic, "Рекао је то. Онда")).isTrue()
    }

    @Test
    fun `before the common era, kr holds only before a lowercase word`() {
        val era = "300. pr. Kr. godine"
        assertThat(ends(latin, era, at = era.indexOf("pr.") + 2)).isFalse()
        assertThat(ends(latin, era, at = era.indexOf("Kr.") + 2)).isFalse()
        assertThat(ends(latin, "pr. Kr. Onda", at = 6)).isTrue()
        val cyrillicEra = "300. пр. Хр. године"
        assertThat(ends(cyrillic, cyrillicEra, at = cyrillicEra.indexOf("Хр.") + 2)).isFalse()
        val bce = "u 3. st. p. n. e. vladao"
        for (letter in listOf("p.", "n.", "e.")) assertThat(ends(latin, bce, at = bce.indexOf(letter) + 1)).isFalse()
        assertThat(ends(latin, "Vitamin E. Onda")).isTrue()
    }

    @Test
    fun `serbian and croatian quotes close with their own marks`() {
        assertThat(latin.quotePairs['„']).isEqualTo('“')
        assertThat(latin.quotePairs['»']).isEqualTo('«')
        assertThat(cyrillic.quotePairs).isEqualTo(latin.quotePairs)
    }

    @Test
    fun `wiktionary recordings are looked for in cyrillic under the serbian prefix`() {
        assertThat(latin.recordingPrefix).isEqualTo("sr")
        assertThat(cyrillic.recordingPrefix).isEqualTo("sr")
        assertThat(latin.recordingTitle("kuća", "")).isEqualTo("кућа")
        assertThat(latin.recordingTitle("Ljubljana", "")).isEqualTo("Љубљана")
        assertThat(cyrillic.recordingTitle("кућа", "")).isEqualTo("кућа")
        // A word with a letter outside the alphabet stays as written.
        assertThat(latin.recordingTitle("wifi", "")).isEqualTo("wifi")
        assertThat(latin.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
    }

    @Test
    fun `a latin j in a cyrillic word becomes the serbian letter only in the cyrillic entry`() {
        assertThat(cyrillic.ocrWord("j\u043e\u0448")).isEqualTo("још")
        assertThat(cyrillic.ocrWord("J\u0435\u0434\u0430\u043d")).isEqualTo("Један")
        // The Russian look-alikes are fixed as well; a word without a Cyrillic letter stays.
        assertThat(cyrillic.ocrWord("\u043co\u0440e")).isEqualTo("море")
        assertThat(cyrillic.ocrWord("jos")).isEqualTo("jos")
        assertThat(latin.ocrWord("j\u043e\u0448")).isEqualTo("j\u043e\u0448")
    }

    @Test
    fun `each entry has its script, voice, flag and code, the dictionaries' code is shared`() {
        assertThat(latin.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(cyrillic.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(latin.speechTag).isEqualTo("hr")
        assertThat(cyrillic.speechTag).isEqualTo("sr")
        assertThat(latin.flagCountries).containsExactly("hr")
        assertThat(cyrillic.flagCountries).containsExactly("rs")
        assertThat(latin.codeLabel).isEqualTo("SH")
        assertThat(cyrillic.codeLabel).isEqualTo("СХ")
        assertThat(latin.glyph).isEqualTo("Đ")
        assertThat(cyrillic.glyph).isEqualTo("Ћ")
        assertThat(Language.withDictionaries("sh")).containsExactly(Language.SERBO_CROATIAN_LATIN, Language.SERBO_CROATIAN_CYRILLIC)
        assertThat(Language.of("sh")).isNull()
        assertThat(Language.of("sh-Cyrl")).isEqualTo(Language.SERBO_CROATIAN_CYRILLIC)
    }

    @Test
    fun `the names say the script`() {
        assertThat(latin.nameIn(Locale.ENGLISH)).isEqualTo("Serbo-Croatian (Latin)")
        assertThat(cyrillic.nameIn(Locale.ENGLISH)).isEqualTo("Serbo-Croatian (Cyrillic)")
    }

    @Test
    fun `the accent marks of readings are stripped from headwords on vowels and r`() {
        val marks = requireNotNull(latin.headwordMarks)
        assertThat(marks.marks.map { it.code }).containsExactly(0x300, 0x301, 0x302, 0x304, 0x30F, 0x311)
        assertThat(marks.bases).isEqualTo("aeiour\u0430\u0435\u0438\u043e\u0443\u0440")
        assertThat(latin.ignoredFormTags).containsExactly("Cyrillic")
    }
}
