package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwedishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = SwedishSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("Frukt, t.ex. äpplen", at = 11)).isFalse()
        assertThat(ends("Vi ses kl. 8")).isFalse()
        assertThat(ends("Det var bl.a. Anna", at = 12)).isFalse()
        assertThat(ends("Se s. 12")).isFalse()
        assertThat(ends("Det tar ung. tre timmar")).isFalse()
        assertThat(ends("Hon är ung. Han är gammal.")).isTrue()
        assertThat(ends("Rom grundades 753 f.Kr. Staden växte.", at = 22)).isTrue()
        assertThat(ends("År 753 f.Kr. grundades Rom.", at = 11)).isFalse()
        assertThat(ends("Anna, Per m.fl. Sedan gick de.", at = 14)).isTrue()
        assertThat(ends("Anna, Per m.fl. kom.", at = 14)).isFalse()
        assertThat(ends("Han bor i St. Louis")).isFalse()
        assertThat(ends("Äpplen, päron osv. Sedan gick vi.", at = 17)).isTrue()
        assertThat(ends("äpplen, päron osv. som vi köpte", at = 17)).isFalse()
        assertThat(ends("böcker, kartor m.m. Allt såldes.", at = 18)).isTrue()
    }

    @Test
    fun `ordinals are written 1 colon a, so a number with a period ends the sentence`() {
        assertThat(SwedishSupport.ordinalPeriod).isFalse()
        assertThat(ends("Han kom 1999. Sedan")).isTrue()
    }

    @Test
    fun `ocr's a with a hook or circumflex is the ring a, other letters and app text stay`() {
        assertThat(SwedishSupport.ocrWord("pả")).isEqualTo("på")
        assertThat(SwedishSupport.ocrWord("Âr")).isEqualTo("År")
        assertThat(SwedishSupport.ocrWord("gâ")).isEqualTo("gå")
        assertThat(SwedishSupport.ocrWord("ẢR")).isEqualTo("ÅR")
        assertThat(SwedishSupport.ocrWord("väder")).isEqualTo("väder")
        assertThat(SwedishSupport.ocrWord("öra")).isEqualTo("öra")
        assertThat(SwedishSupport.ocrLines(listOf(listOf("pả", "väg")))).containsExactly(listOf("på", "väg"))
    }

    @Test
    fun `compounds of up to 24 letters split at s, e, u, o and a, latin letters, wiktionary first`() {
        assertThat(SwedishSupport.defaultScanLength).isEqualTo(24)
        assertThat(SwedishSupport.compoundLinks).containsExactly("s", "e", "u", "o", "a").inOrder()
        assertThat(SwedishSupport.capitalizedNouns).isFalse()
        assertThat(SwedishSupport.fontSubsets).containsExactly("latin")
        assertThat(SwedishSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(SwedishSupport.linguaLibreItems).containsExactly("Q9027")
    }

    @Test
    fun `apostrophes stay inside words`() {
        assertThat(SwedishSupport.wordStartOffset("Kalle'", "s")).isEqualTo(6)
    }
}
