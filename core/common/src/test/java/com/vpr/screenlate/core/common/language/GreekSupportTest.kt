package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GreekSupportTest {

    private val support = GreekSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the question mark is a semicolon, the ano teleia ends nothing`() {
        assertThat(ends("Τι κάνεις; Καλά", at = 9)).isTrue()
        assertThat(ends("Τι κάνεις\u037E Καλά", at = 9)).isTrue()
        assertThat(ends("Ήρθε· έφυγε", at = 4)).isFalse()
        assertThat(ends("Ήρθε. Έφυγε")).isTrue()
    }

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("π.χ. μήλα", at = 3)).isFalse()
        assertThat(ends("ο κ. Παπαδόπουλος")).isFalse()
        assertThat(ends("τον 19ο αι. οι")).isFalse()
        assertThat(ends("βλ. σελ. 5")).isFalse()
        assertThat(ends("ΒΛ. ΠΙΟ ΚΑΤΩ")).isFalse()
        assertThat(ends("μήλα κ.λπ. Μετά", at = 9)).isTrue()
        assertThat(ends("το 1453 μ.Χ. Μετά", at = 11)).isTrue()
    }

    @Test
    fun `guillemets hold the curly quotes inside`() {
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs['“']).isEqualTo('”')
    }

    @Test
    fun `latin look-alikes in a greek word or alone become greek`() {
        assertThat(support.ocrWord("T\u03bf")).isEqualTo("Το")
        assertThat(support.ocrWord("\u03b5v\u03b1")).isEqualTo("ενα")
        assertThat(support.ocrWord("τηc")).isEqualTo("της")
        // The articles ο and Η have no other letter to show their script.
        assertThat(support.ocrWord("o")).isEqualTo("ο")
        assertThat(support.ocrWord("H")).isEqualTo("Η")
        assertThat(support.ocrWord("iPhone")).isEqualTo("iPhone")
        assertThat(support.ocrWord("TAXI")).isEqualTo("TAXI")
    }

    @Test
    fun `wordfreq's sigma ends a word as a final sigma`() {
        assertThat(support.frequencyWord("τησ")).isEqualTo("της")
        assertThat(support.frequencyWord("σ'αυτούσ")).isEqualTo("σ'αυτούς")
        assertThat(support.frequencyWord("σ")).isEqualTo("σ")
        assertThat(support.frequencyWord("σασ")).isEqualTo("σας")
        assertThat(support.frequencyWord("σ.σ")).isEqualTo("σ.σ")
        assertThat(support.frequencySpellings("της")).isEmpty()
    }

    @Test
    fun `a word after an elided one is looked up from its start`() {
        assertThat(support.wordStartOffset("σ'αγα", "π")).isEqualTo(3)
        // θα'ναι: the apostrophe takes the vowel of the next word, which stays joined.
        assertThat(support.wordStartOffset("θα'ν", "α")).isEqualTo(4)
    }

    @Test
    fun `every headword is also found without its tonos, the cloud and paddle read greek`() {
        assertThat(support.headwordMarks).isEqualTo(HeadwordMarks(marks = "\u0301", bases = "αεηιουω", everyHeadword = true))
        assertThat(support.ocrScript).isEqualTo(OcrScript.GREEK)
        assertThat(support.systemFonts.unicodeRange).contains("U+0370-03FF")
        assertThat(support.fontSubsets).containsExactly("latin", "greek")
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("gr")
        assertThat(support.glyph).isEqualTo("Ω")
    }
}
