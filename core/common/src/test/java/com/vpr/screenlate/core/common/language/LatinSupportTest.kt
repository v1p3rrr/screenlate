package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class LatinSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = LatinSupport.endsSentence(text, at)

    @Test
    fun `praenomina before a name keep the sentence going, the same words in lowercase end it`() {
        assertThat(ends("C. Iulius Caesar")).isFalse()
        assertThat(ends("M. Tullius Cicero")).isFalse()
        assertThat(ends("Sex. Pompeius Magnus")).isFalse()
        assertThat(ends("Ti. Gracchus")).isFalse()
        assertThat(ends("Ecclesia S. Mariae")).isFalse()
        assertThat(ends("Liberi erant sex. Deinde")).isTrue()
        assertThat(ends("Altitudo est decem m. Deinde")).isTrue()
    }

    @Test
    fun `abbreviations do not end a sentence, eras and etc before a capital do`() {
        assertThat(ends("cf. Plinium")).isFalse()
        assertThat(ends("Urbes, e.g. Roma", at = 10)).isFalse()
        assertThat(ends("Urbes, i.e. Roma", at = 10)).isFalse()
        assertThat(ends("Circa c. 300 milites")).isFalse()
        assertThat(ends("Natus anno 44 a.C.n. in urbe", at = 19)).isFalse()
        assertThat(ends("Mortuus est anno 44 a.C.n. Deinde", at = 25)).isTrue()
        assertThat(ends("Poma, pira etc. Postea")).isTrue()
        assertThat(ends("poma, pira etc. et alia")).isFalse()
        assertThat(ends("Roma urbs est. Ibi")).isTrue()
    }

    @Test
    fun `a number with a period ends the sentence before a capital`() {
        assertThat(LatinSupport.ordinalPeriod).isFalse()
        assertThat(ends("Natus est anno 1999. Deinde")).isTrue()
        assertThat(ends("pi est 3.14")).isFalse()
    }

    @Test
    fun `english quotes and guillemets pair, reversed guillemets do not`() {
        assertThat(LatinSupport.quotePairs).containsEntry('“', '”')
        assertThat(LatinSupport.quotePairs).containsEntry('«', '»')
        assertThat(LatinSupport.quotePairs).doesNotContainKey('»')
        assertThat(LatinSupport.quotePairs).doesNotContainKey('„')
    }

    @Test
    fun `lemma names lose their macrons and breves, rules apply only where the text has no entry`() {
        assertThat(LatinSupport.headwordMarks).isEqualTo(HeadwordMarks(marks = "\u0304\u0306", bases = "aeiouy"))
        assertThat(LatinSupport.headwordMarks!!.lemmasAsWritten).isFalse()
        assertThat(LatinSupport.inflectionRulesOnlyWithoutEntry).isTrue()
    }

    @Test
    fun `frequency words lose their macrons and breves, other marks stay`() {
        assertThat(LatinSupport.frequencyWord("vīta")).isEqualTo("vita")
        assertThat(LatinSupport.frequencyWord("amīcīs")).isEqualTo("amicis")
        assertThat(LatinSupport.frequencyWord("hŭius")).isEqualTo("huius")
        assertThat(LatinSupport.frequencyWord("poëta")).isEqualTo("poëta")
        assertThat(LatinSupport.frequencyWord("cælum")).isEqualTo("cælum")
    }

    @Test
    fun `recordings and transcriptions name the classical or the ecclesiastical pronunciation`() {
        assertThat(LatinSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH").inOrder()
        assertThat(LatinSupport.audioRegions.map { it.id }).containsExactly("cls", "ecc").inOrder()
        assertThat(LatinSupport.audioRegions.map { it.codes }).containsExactly(setOf("cls"), setOf("ecc")).inOrder()
        val tags = listOf("Classical-Latin", "\u26EA", "Ecclesiastical", "IPA")
        assertThat(TranscriptionRegions.of(tags, LatinSupport.transcriptionRegionNames)).containsExactly("cls", "ecc").inOrder()
    }

    @Test
    fun `latin's own code, name, flag and letters`() {
        assertThat(Language.of("la")).isEqualTo(Language.LATIN)
        assertThat(LatinSupport.languageTag).isEqualTo("la")
        assertThat(LatinSupport.nameIn(Locale.ENGLISH)).isEqualTo("Latin")
        assertThat(LatinSupport.codeLabel).isEqualTo("LA")
        assertThat(LatinSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(LatinSupport.flagCountries).containsExactly("lang_la")
        assertThat(LatinSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(LatinSupport.fontLetters).isEqualTo("āēīōūæœ")
        assertThat(LatinSupport.compoundLinks).isNull()
    }
}
