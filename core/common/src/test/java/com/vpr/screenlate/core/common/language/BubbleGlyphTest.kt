package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class BubbleGlyphTest {

    @Test
    fun `a language shows the first letter of its own name`() {
        assertThat(bubbleGlyph(Language.GERMAN, emptyList())).isEqualTo("D")
        assertThat(bubbleGlyph(Language.RUSSIAN, emptyList())).isEqualTo("Р")
        assertThat(bubbleGlyph(Language.CZECH, emptyList())).isEqualTo("Č")
        assertThat(bubbleGlyph(Language.ARABIC, emptyList())).isEqualTo("ع")
        assertThat(bubbleGlyph(Language.PERSIAN, emptyList())).isEqualTo("ف")
        assertThat(bubbleGlyph(Language.JAPANESE, emptyList())).isEqualTo("あ")
    }

    @Test
    fun `every latin and cyrillic language shows one capital of its script`() {
        for (language in Language.entries.filter { it.support.ocrScript in setOf(OcrScript.LATIN, OcrScript.CYRILLIC) }) {
            val glyph = language.support.glyph
            val script = if (language.support.ocrScript == OcrScript.LATIN) {
                Character.UnicodeScript.LATIN
            } else {
                Character.UnicodeScript.CYRILLIC
            }
            assertThat(glyph.length).isEqualTo(1)
            assertThat(glyph[0].isUpperCase()).isTrue()
            assertThat(Character.UnicodeScript.of(glyph[0].code)).isEqualTo(script)
        }
    }

    @Test
    fun `quick languages with the same letter show their codes`() {
        val quick = listOf(Language.ENGLISH, Language.SPANISH, Language.JAPANESE)
        assertThat(bubbleGlyph(Language.ENGLISH, quick)).isEqualTo("EN")
        assertThat(bubbleGlyph(Language.SPANISH, quick)).isEqualTo("ES")
        assertThat(bubbleGlyph(Language.JAPANESE, quick)).isEqualTo("あ")
    }

    @Test
    fun `letters alike across latin and cyrillic count as the same`() {
        val quick = listOf(Language.RUSSIAN, Language.POLISH)
        assertThat(bubbleGlyph(Language.RUSSIAN, quick)).isEqualTo("РУ")
        assertThat(bubbleGlyph(Language.POLISH, quick)).isEqualTo("PL")
        assertThat(bubbleGlyph(Language.SERBIAN, listOf(Language.SERBIAN, Language.WELSH))).isEqualTo("СР")
        assertThat(bubbleGlyph(Language.KYRGYZ, listOf(Language.KYRGYZ, Language.SWAHILI))).isEqualTo("КЫ")
        assertThat(bubbleGlyph(Language.SWAHILI, listOf(Language.KYRGYZ, Language.SWAHILI))).isEqualTo("SW")
    }

    @Test
    fun `a letter with marks counts as the letter without them`() {
        val quick = listOf(Language.CZECH, Language.WELSH)
        assertThat(bubbleGlyph(Language.CZECH, quick)).isEqualTo("CS")
        assertThat(bubbleGlyph(Language.WELSH, quick)).isEqualTo("CY")
    }

    @Test
    fun `different letters keep the glyphs`() {
        val quick = listOf(Language.KAZAKH, Language.KYRGYZ, Language.UKRAINIAN)
        assertThat(bubbleGlyph(Language.KAZAKH, quick)).isEqualTo("Қ")
        assertThat(bubbleGlyph(Language.KYRGYZ, quick)).isEqualTo("К")
        assertThat(bubbleGlyph(Language.UKRAINIAN, quick)).isEqualTo("У")
        val arabic = listOf(Language.ARABIC, Language.PERSIAN)
        assertThat(bubbleGlyph(Language.ARABIC, arabic)).isEqualTo("ع")
        assertThat(bubbleGlyph(Language.PERSIAN, arabic)).isEqualTo("ف")
    }

    @Test
    fun `a photo's language outside the quick ones is compared with them`() {
        assertThat(bubbleGlyph(Language.SPANISH, listOf(Language.ENGLISH, Language.JAPANESE))).isEqualTo("ES")
        assertThat(bubbleGlyph(Language.GERMAN, listOf(Language.ENGLISH, Language.JAPANESE))).isEqualTo("D")
    }

    @Test
    fun `cyrillic languages write their codes in cyrillic, the others in latin capitals`() {
        for (language in Language.entries) {
            val code = language.support.codeLabel
            if (language.support.ocrScript == OcrScript.CYRILLIC) {
                assertThat(code.length).isEqualTo(2)
                assertThat(code.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC }).isTrue()
            } else {
                assertThat(code).isEqualTo(language.code.uppercase(Locale.ROOT))
            }
        }
        assertThat(RussianSupport.codeLabel).isEqualTo("РУ")
        assertThat(UkrainianSupport.codeLabel).isEqualTo("УК")
        assertThat(BelarusianSupport.codeLabel).isEqualTo("БЕ")
        assertThat(KazakhSupport.codeLabel).isEqualTo("КК")
    }
}
