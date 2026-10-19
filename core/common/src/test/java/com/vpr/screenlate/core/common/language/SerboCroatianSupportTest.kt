package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class SerboCroatianSupportTest {

    private val croatian = CroatianSupport
    private val serbian = SerbianSupport

    private fun ends(support: LanguageSupport, text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `ordinals, the era and abbreviations do not end a sentence in either script or language`() {
        for (support in listOf(croatian, serbian)) {
            assertThat(ends(support, "Dana 5. maja")).isFalse()
            assertThat(ends(support, "Дана 5. маја")).isFalse()
            assertThat(ends(support, "Godine 1918. godine")).isFalse()
            assertThat(ends(support, "Bilo je to 1990. Onda")).isTrue()
        }
        assertThat(ends(croatian, "Voće, npr. jabuke")).isFalse()
        assertThat(ends(serbian, "Воће, нпр. јабуке")).isFalse()
        assertThat(ends(croatian, "u 19. st. se", at = 8)).isFalse()
        assertThat(ends(croatian, "Razgovarao je s dr. Petrovićem")).isFalse()
        assertThat(ends(croatian, "Rekao je to. Onda")).isTrue()
        assertThat(ends(serbian, "Рекао је то. Онда")).isTrue()
    }

    @Test
    fun `before the common era, kr holds only before a lowercase word`() {
        val era = "300. pr. Kr. godine"
        assertThat(ends(croatian, era, at = era.indexOf("pr.") + 2)).isFalse()
        assertThat(ends(croatian, era, at = era.indexOf("Kr.") + 2)).isFalse()
        assertThat(ends(croatian, "pr. Kr. Onda", at = 6)).isTrue()
        val cyrillicEra = "300. пр. Хр. године"
        assertThat(ends(serbian, cyrillicEra, at = cyrillicEra.indexOf("Хр.") + 2)).isFalse()
        val bce = "u 3. st. p. n. e. vladao"
        for (letter in listOf("p.", "n.", "e.")) assertThat(ends(croatian, bce, at = bce.indexOf(letter) + 1)).isFalse()
        assertThat(ends(croatian, "Vitamin E. Onda")).isTrue()
    }

    @Test
    fun `serbian and croatian quotes close with their own marks`() {
        assertThat(croatian.quotePairs['„']).isEqualTo('“')
        assertThat(croatian.quotePairs['»']).isEqualTo('«')
        assertThat(serbian.quotePairs).isEqualTo(croatian.quotePairs)
    }

    @Test
    fun `croatian looks for croatian wiktionary recordings, then serbian ones in cyrillic`() {
        assertThat(croatian.recordings("kuća", "")).containsExactly(RecordingTitle("hr", "kuća"), RecordingTitle("sr", "кућа")).inOrder()
        assertThat(croatian.recordings("Ljubljana", "")).contains(RecordingTitle("sr", "Љубљана"))
        // A word with a letter outside the alphabet has no Cyrillic spelling.
        assertThat(croatian.recordings("wifi", "")).containsExactly(RecordingTitle("hr", "wifi"))
        assertThat(croatian.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
    }

    @Test
    fun `serbian looks for its wiktionary recordings in cyrillic, then in latin letters`() {
        val both = listOf(RecordingTitle("sr", "кућа"), RecordingTitle("sr", "kuća"))
        assertThat(serbian.recordings("kuća", "")).containsExactlyElementsIn(both).inOrder()
        assertThat(serbian.recordings("кућа", "")).containsExactlyElementsIn(both).inOrder()
        assertThat(serbian.recordings("wifi", "")).containsExactly(RecordingTitle("sr", "wifi"))
    }

    @Test
    fun `lingua libre has serbian speakers after croatian ones, titled in latin letters`() {
        assertThat(croatian.linguaLibreItems).containsExactly("Q6654", "Q9299").inOrder()
        assertThat(croatian.linguaLibreTitle("kuća")).isEqualTo("kuća")
        assertThat(serbian.linguaLibreItems).containsExactly("Q9299")
        assertThat(serbian.linguaLibreTitle("кућа")).isEqualTo("kuća")
        assertThat(serbian.linguaLibreTitle("Љубав")).isEqualTo("Ljubav")
        assertThat(serbian.linguaLibreTitle("voda")).isEqualTo("voda")
    }

    @Test
    fun `the serbian voice is given latin words in cyrillic`() {
        assertThat(serbian.speechText("kuća", "")).isEqualTo("кућа")
        assertThat(serbian.speechText("кућа", "")).isEqualTo("кућа")
        assertThat(serbian.speechText("wifi", "")).isEqualTo("wifi")
        assertThat(croatian.speechText("kuća", "")).isEqualTo("kuća")
    }

    @Test
    fun `serbian text in latin letters is translated as serbian latin`() {
        assertThat(serbian.translationTag("Kuća je velika.")).isEqualTo("sr-Latn")
        assertThat(serbian.translationTag("Кућа је велика.")).isNull()
        // Look-alikes alone say nothing about the script.
        assertThat(serbian.translationTag("OKO")).isNull()
        assertThat(croatian.translationTag("Kuća je velika.")).isNull()
    }

    @Test
    fun `a latin j in a cyrillic word becomes the serbian letter`() {
        assertThat(serbian.ocrWord("j\u043e\u0448")).isEqualTo("још")
        assertThat(serbian.ocrWord("J\u0435\u0434\u0430\u043d")).isEqualTo("Један")
        // The Russian look-alikes are fixed as well; a word without a Cyrillic letter stays.
        assertThat(serbian.ocrWord("\u043co\u0440e")).isEqualTo("море")
        assertThat(serbian.ocrWord("jos")).isEqualTo("jos")
        assertThat(croatian.ocrWord("j\u043e\u0448")).isEqualTo("j\u043e\u0448")
    }

    @Test
    fun `serbian fixes look-alikes only on cyrillic lines, and on lines of look-alikes alone by the page`() {
        val latinLine = listOf("Ja", "i", "ti", "smo", "u", "kući")
        val cyrillicLine = listOf("Ja", "\u0441\u0430\u043c", "o", "\u043a\u0443\u045b\u0438")
        assertThat(serbian.ocrLines(listOf(latinLine))).containsExactly(latinLine)
        // On a Cyrillic line a word of look-alikes alone (Ja) is Cyrillic too; one with another Latin letter stays.
        assertThat(serbian.ocrLines(listOf(cyrillicLine)).single()).containsExactly("Ја", "сам", "о", "кући").inOrder()
        assertThat(serbian.ocrLines(listOf(listOf("Joj", "Tom", "кући"))).single()).containsExactly("Јој", "Tom", "кући").inOrder()
        // A line of look-alikes alone ("O") follows the page.
        assertThat(serbian.ocrLines(listOf(cyrillicLine, listOf("O"))).last()).containsExactly("О")
        assertThat(serbian.ocrLines(listOf(latinLine, listOf("O"))).last()).containsExactly("O")
        assertThat(croatian.ocrLines(listOf(listOf("o", "\u043a\u0443\u045b\u0438"))).single()).containsExactly("o", "кући").inOrder()
    }

    @Test
    fun `serbian keeps latin words in capitals on a cyrillic line unless the line is a heading in capitals`() {
        val text = listOf("Кликни", "OK", "и", "TOP", "листа", "Joj,", "TEXT")
        assertThat(serbian.ocrLines(listOf(text)).single())
            .containsExactly("Кликни", "OK", "и", "TOP", "листа", "Јој,", "TEXT").inOrder()
        // In a heading in capitals the words of look-alikes alone are Serbian too (ЈОЈ, ОВО ЈЕ КУЋА).
        assertThat(serbian.ocrLines(listOf(listOf("JOJ,", "OBO", "JE", "КУЋА"))).single())
            .containsExactly("ЈОЈ,", "ОВО", "ЈЕ", "КУЋА").inOrder()
        // A Cyrillic abbreviation does not make running text a heading.
        assertThat(serbian.ocrLines(listOf(listOf("OK", "у", "САД", "сад"))).single())
            .containsExactly("OK", "у", "САД", "сад").inOrder()
        // A line in capitals of look-alikes alone (a button, OK) follows the page, and keeps its Latin capitals.
        assertThat(serbian.ocrLines(listOf(listOf("кући"), listOf("OK"))).last()).containsExactly("OK")
        // A Cyrillic word in capitals with Latin look-alikes in it is fixed anyway.
        assertThat(serbian.ocrLines(listOf(listOf("кући", "KУЋA"))).single())
            .containsExactly("кући", "КУЋА").inOrder()
    }

    @Test
    fun `each language has its script, voice, flag and code, the dictionaries' code is shared`() {
        assertThat(croatian.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(serbian.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(croatian.speechTag).isEqualTo("hr")
        assertThat(serbian.speechTag).isEqualTo("sr")
        assertThat(croatian.languageTag).isEqualTo("hr")
        assertThat(serbian.languageTag).isEqualTo("sr")
        assertThat(croatian.flagCountries).containsExactly("hr")
        assertThat(serbian.flagCountries).containsExactly("rs")
        assertThat(croatian.codeLabel).isEqualTo("HR")
        assertThat(serbian.codeLabel).isEqualTo("SR")
        assertThat(croatian.glyph).isEqualTo("Đ")
        assertThat(serbian.glyph).isEqualTo("Ћ")
        assertThat(Language.withDictionaries("sh")).containsExactly(Language.CROATIAN, Language.SERBIAN).inOrder()
        assertThat(Language.of("sh")).isNull()
        assertThat(Language.of("sr")).isEqualTo(Language.SERBIAN)
    }

    @Test
    fun `the names are the system's`() {
        assertThat(croatian.nameIn(Locale.ENGLISH)).isEqualTo("Croatian")
        assertThat(serbian.nameIn(Locale.ENGLISH)).isEqualTo("Serbian")
    }

    @Test
    fun `the accent marks of readings are stripped from headwords on vowels and r`() {
        val marks = requireNotNull(croatian.headwordMarks)
        assertThat(marks.marks.map { it.code }).containsExactly(0x300, 0x301, 0x302, 0x304, 0x30F, 0x311)
        assertThat(marks.bases).isEqualTo("aeiour\u0430\u0435\u0438\u043e\u0443\u0440")
        assertThat(serbian.ignoredFormTags).containsExactly("Cyrillic")
    }
}
