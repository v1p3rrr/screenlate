package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class HungarianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = HungarianSupport.endsSentence(text, at)

    @Test
    fun `dates, ordinals and abbreviations do not end a sentence`() {
        assertThat(ends("2023. május 3-án")).isFalse()
        assertThat(ends("a 19. század")).isFalse()
        assertThat(ends("gyümölcs, pl. alma")).isFalse()
        assertThat(ends("gyümölcs, pl. Alma")).isFalse()
        assertThat(ends("ld. a 4. oldalt")).isFalse()
        assertThat(ends("ifj. Kovács")).isFalse()
        assertThat(ends("alma, körte stb. és más")).isFalse()
        assertThat(ends("alma, körte stb. Aztán")).isTrue()
        assertThat(ends("a 19. sz. végén", "a 19. sz".length)).isFalse()
        assertThat(ends("2023. jan. 5.", "2023. jan".length)).isFalse()
        assertThat(ends("Ez 1918-ban történt. Aztán")).isTrue()
        assertThat(ends("A ház nagy. A kert kicsi")).isTrue()
    }

    @Test
    fun `an ending after a hyphen is looked up with the hyphen after a number or an abbreviation in capitals`() {
        assertThat(HungarianSupport.partStarts("2023-ban", 0, "2023-b".length)).containsExactly(4, 5).inOrder()
        assertThat(HungarianSupport.partStarts("3-án", 0, "3-á".length)).containsExactly(1, 2).inOrder()
        assertThat(HungarianSupport.partStarts("EU-ban", 0, "EU-b".length)).containsExactly(2, 3).inOrder()
        // A hyphen after a word joins a compound or a pair.
        assertThat(HungarianSupport.partStarts("magyar-angol", 0, "magyar-a".length)).containsExactly(7)
        assertThat(HungarianSupport.lookupStart("-ban", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(HungarianSupport.lookupStart("- ban", latinAsNative = false)).isNull()
    }

    @Test
    fun `low-high quotes outside, guillemets pointing inward inside, english quotes too`() {
        assertThat(HungarianSupport.quotePairs).containsEntry('„', '”')
        assertThat(HungarianSupport.quotePairs).containsEntry('»', '«')
        assertThat(HungarianSupport.quotePairs).containsEntry('“', '”')
        assertThat(HungarianSupport.quotePairs).doesNotContainKey('«')
    }

    @Test
    fun `rules only where the text has no entry, compounds split without links`() {
        assertThat(HungarianSupport.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(HungarianSupport.compoundLinks).isEmpty()
        assertThat(HungarianSupport.headwordMarks).isNull()
        assertThat(HungarianSupport.fallbackLanguage).isNull()
        assertThat(Language.HUNGARIAN.lookupDictionaryCodes).containsExactly("hu")
        assertThat(HungarianSupport.defaultScanLength).isEqualTo(16)
    }

    @Test
    fun `definite and indefinite name the conjugations`() {
        assertThat(HungarianSupport.formTagAliases).containsExactly(
            "definite", "object-definite",
            "indefinite", "object-indefinite",
        )
        assertThat(FinnishSupport.formTagAliases).isEmpty()
    }

    @Test
    fun `recognized text is taken as read, the bundle tries the double acute letters`() {
        assertThat(HungarianSupport.ocrWord("füzfa")).isEqualTo("füzfa")
        assertThat(HungarianSupport.ocrWord("2023-ban")).isEqualTo("2023-ban")
    }

    @Test
    fun `hungarian's own code, flag and letters`() {
        assertThat(Language.of("hu")).isEqualTo(Language.HUNGARIAN)
        assertThat(HungarianSupport.languageTag).isEqualTo("hu")
        assertThat(HungarianSupport.localeLanguages).containsExactly("hu")
        assertThat(HungarianSupport.codeLabel).isEqualTo("HU")
        assertThat(HungarianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(HungarianSupport.flagCountries).containsExactly("hu")
        assertThat(HungarianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(HungarianSupport.fontSample.lowercase().toSet()).containsAtLeast('ő', 'ű', 'ö', 'ü', 'á', 'é')
        assertThat(HungarianSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(HungarianSupport.glyph).isEqualTo("M")
    }
}
