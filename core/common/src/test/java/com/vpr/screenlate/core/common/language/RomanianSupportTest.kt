package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class RomanianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = RomanianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("Dl. Popescu a venit")).isFalse()
        assertThat(ends("Vezi nr. 5 din lege")).isFalse()
        assertThat(ends("Pe str. Lipscani")).isFalse()
        assertThat(ends("Biserica din sec. XIV")).isFalse()
        assertThat(ends("Satul are cca. 300 de case")).isFalse()
        assertThat(ends("Cărți, reviste etc. Apoi am plecat.")).isTrue()
        assertThat(ends("cărți, reviste etc. și altele")).isFalse()
        assertThat(ends("Mere, pere ș.a. au fost", at = 15)).isFalse()
        assertThat(ends("Cezar a murit în 44 î.Hr. Apoi", at = 24)).isTrue()
        assertThat(ends("În 44 î.Hr. a murit Cezar", at = 10)).isFalse()
        assertThat(ends("Ședința a avut loc. Apoi au plecat")).isTrue()
    }

    @Test
    fun `a number with a period ends the sentence before a capital`() {
        assertThat(RomanianSupport.ordinalPeriod).isFalse()
        assertThat(ends("S-a născut în 1999. Apoi")).isTrue()
        assertThat(ends("Capitolul 5. Introducere")).isTrue()
        assertThat(ends("pi este 3.14")).isFalse()
    }

    @Test
    fun `quotes open low and close high, guillemets point outward`() {
        assertThat(RomanianSupport.quotePairs).containsEntry('„', '”')
        assertThat(RomanianSupport.quotePairs).containsEntry('“', '”')
        assertThat(RomanianSupport.quotePairs).containsEntry('«', '»')
        assertThat(RomanianSupport.quotePairs).doesNotContainKey('»')
    }

    @Test
    fun `a pronoun or preposition joined with a hyphen starts a new word`() {
        assertThat(RomanianSupport.wordStartOffset("Ea s-", "a dus")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("l-", "am văzut")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("Și-", "a luat")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("şi-", "a")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("într-", "o zi")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("dintr-", "un oraș")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("intr-", "o")).isEqualTo(0)
        assertThat(RomanianSupport.wordStartOffset("ne-", "am dus")).isEqualTo(0)
        // A word before the pronoun keeps it, as do names and compounds.
        assertThat(RomanianSupport.wordStartOffset("să-", "l văd")).isEqualTo(3)
        assertThat(RomanianSupport.wordStartOffset("nu-", "i place")).isEqualTo(3)
        assertThat(RomanianSupport.wordStartOffset("Baia-", "Mare")).isEqualTo(5)
        // A word written with such a part (într-adevăr) is found whole aimed at its start.
        assertThat(RomanianSupport.wordStartOffset("într-adevă", "r")).isEqualTo(5)
        assertThat(RomanianSupport.wordStartOffset("s", "-a")).isEqualTo(0)
    }

    @Test
    fun `every headword is also keyed without its marks and with comma letters for cedilla ones`() {
        assertThat(RomanianSupport.headwordMarks).isEqualTo(
            HeadwordMarks(
                marks = "\u0306\u0302\u0326\u0327",
                bases = "aist",
                everyHeadword = true,
                replacements = mapOf("ş" to "ș", "ţ" to "ț", "Ş" to "Ș", "Ţ" to "Ț"),
            ),
        )
        assertThat(RomanianSupport.headwordMarks!!.lemmasAsWritten).isTrue()
    }

    @Test
    fun `rules only where the text has no entry, cyrillic headwords left out`() {
        assertThat(RomanianSupport.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(RomanianSupport.showsHeadword("este")).isTrue()
        assertThat(RomanianSupport.showsHeadword("țară")).isTrue()
        assertThat(RomanianSupport.showsHeadword("есте")).isFalse()
        assertThat(RomanianSupport.showsHeadword("о")).isFalse()
        assertThat(RomanianSupport.ignoredFormTags).isEmpty()
    }

    @Test
    fun `romanian's own code, name and voice`() {
        assertThat(Language.of("ro")).isEqualTo(Language.ROMANIAN)
        assertThat(RomanianSupport.languageTag).isEqualTo("ro")
        assertThat(RomanianSupport.speechTag).isEqualTo("ro")
        assertThat(RomanianSupport.localeLanguages).containsExactly("ro")
        assertThat(RomanianSupport.nameIn(Locale.ENGLISH)).isEqualTo("Romanian")
        assertThat(RomanianSupport.codeLabel).isEqualTo("RO")
    }

    @Test
    fun `latin letters with the extended subset, lingua libre first`() {
        assertThat(RomanianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(RomanianSupport.compoundLinks).isNull()
        assertThat(RomanianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(RomanianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(RomanianSupport.linguaLibreItems).containsExactly("Q7913")
        assertThat(RomanianSupport.flagCountries).containsExactly("ro")
        assertThat(RomanianSupport.glyph).isEqualTo("Ș")
    }
}
