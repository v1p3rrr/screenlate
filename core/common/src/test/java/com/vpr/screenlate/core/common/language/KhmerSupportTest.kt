package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class KhmerSupportTest {

    @Test
    fun `the lookup starts at the start of the ICU word under the aim`() {
        // ខ្ញុំ|ទៅ|សាលារៀន|រាល់ថ្ងៃ
        assertThat(IcuKhmer.wordStartOffset("ខ្ញុំទៅសាលា", "រ", "ៀនរាល់ថ្ងៃ")).isEqualTo("សាលា".length)
        assertThat(IcuKhmer.wordStartOffset("ខ្ញុំ", "ទ", "ៅសាលារៀន")).isEqualTo(0)
        // A consonant below another and the vowels around it belong to the cluster's word.
        assertThat(IcuKhmer.wordStartOffset("ខ្", "ញ", "ុំទៅ")).isEqualTo(2)
        assertThat(IcuKhmer.wordStartOffset("ខ្ញ", "ុ", "ំទៅ")).isEqualTo(3)
        // ICU parts ៗ from its word (កូន|ៗ); aimed at it, the lookup starts at the word it repeats.
        assertThat(IcuKhmer.wordStartOffset("កូន", "ៗ", "របស់គាត់")).isEqualTo(3)
        assertThat(IcuKhmer.wordStartOffset("ខ្ញុំ", "\u200B", "ទៅ")).isEqualTo(0)
        assertThat(IcuKhmer.wordStartOffset("តម្លៃ ", "១", "០០០")).isEqualTo(0)
    }

    @Test
    fun `the lookup also starts up to two ICU words before the aimed one, past zero-width spaces and the repetition mark`() {
        assertThat(IcuKhmer.earlierStartOffset("ខ្ញុំទៅ", "សាលារៀន")).isEqualTo("ខ្ញុំទៅ".length)
        assertThat(IcuKhmer.earlierStartOffset("ខ្ញុំ\u200Bទៅ\u200B", "សាលារៀន")).isEqualTo("ខ្ញុំ\u200Bទៅ\u200B".length)
        assertThat(IcuKhmer.earlierStartOffset("កូនៗ", "របស់គាត់")).isEqualTo("កូនៗ".length)
        // Not across a space or the end of a sentence.
        assertThat(IcuKhmer.earlierStartOffset("ភ្នំពេញ។ ", "ខ្ញុំ")).isEqualTo(0)
        assertThat(IcuKhmer.earlierStartOffset("ខ្ញុំទៅ ", "សាលារៀន")).isEqualTo(0)
        assertThat(IcuKhmer.wordStarts("ខ្ញុំទៅសាលារៀន", "ខ្ញុំទៅ".length)).containsExactly(0, "ខ្ញុំ".length).inOrder()
    }

    @Test
    fun `inside a word the lookup may also start at each cluster up to the aimed one`() {
        // ICU takes ក្រុងភ្នំពេញ (the city of Phnom Penh) as one word; ក្រ ុ ង | ភ្នំ | ពេ ញ.
        assertThat(Icu4jSegmenter.boundaries("ក្រុងភ្នំពេញជា", Locale.forLanguageTag("km")).asList())
            .containsExactly(0, "ក្រុងភ្នំពេញ".length, "ក្រុងភ្នំពេញជា".length).inOrder()
        assertThat(IcuKhmer.innerStarts("ក្រុងភ្នំពេញជា", 0, "ក្រុងភ្នំពេ".length)).containsExactly(4, 5, 9, 11).inOrder()
        // Not past the aimed character, not at a subscript consonant, a mark or the repetition mark.
        assertThat(IcuKhmer.innerStarts("ក្រុងភ្នំពេញជា", 0, "ក្រុងភ្".length)).containsExactly(4, 5).inOrder()
        assertThat(IcuKhmer.innerStarts("កូនៗ", 0, "កូន".length)).containsExactly("កូ".length)
        assertThat(IcuKhmer.innerStarts("ទៅ", 0, 1)).isEmpty()
    }

    @Test
    fun `no match ends inside a cluster, and shorter ones show only where ICU ends a word`() {
        // ខ, ខ្ and ខ្ញ end before a coeng, a subscript consonant and a vowel sign.
        assertThat(IcuKhmer.shownMatchLengths("ខ្ញុំទៅ", setOf(1, 2, 3, 5))).containsExactly(5)
        // សាលារៀន|រាល់ថ្ងៃ: សាលា ends inside the word ICU finds.
        assertThat(IcuKhmer.shownMatchLengths("សាលារៀនរាល់ថ្ងៃ", setOf(4, 7))).containsExactly(7)
        // ក្មេង|ៗ: the word and the word with its repetition mark.
        assertThat(IcuKhmer.shownMatchLengths("ក្មេងៗលេង", setOf(5, 6))).containsExactly(5, 6)
    }

    @Test
    fun `a word starts at a letter or digit outside a cluster, not at the repetition mark`() {
        assertThat(IcuKhmer.lookupStart("ខ្ញុំ", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(IcuKhmer.lookupStart("១០០០", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(IcuKhmer.lookupStart("្ញុំ", latinAsNative = false)).isNull()
        assertThat(IcuKhmer.lookupStart("ុំ", latinAsNative = false)).isNull()
        assertThat(IcuKhmer.lookupStart("ាល", latinAsNative = false)).isNull()
        assertThat(IcuKhmer.lookupStart("ៗលេង", latinAsNative = false)).isNull()
        assertThat(IcuKhmer.lookupStart("\u200Bទៅ", latinAsNative = false)).isNull()
    }

    @Test
    fun `khan, the section end, question and exclamation marks end a sentence, a space or period does not`() {
        assertThat(KhmerSupport.endsSentence("ខ្ញុំទៅ។ គាត់", "ខ្ញុំទៅ".length)).isTrue()
        assertThat(KhmerSupport.endsSentence("ចប់៕", "ចប់".length)).isTrue()
        assertThat(KhmerSupport.endsSentence("សុខសប្បាយទេ?", "សុខសប្បាយទេ".length)).isTrue()
        assertThat(KhmerSupport.endsSentence("ខ្ញុំទៅ សាលា", "ខ្ញុំទៅ".length)).isFalse()
        assertThat(KhmerSupport.endsSentence("ព.ស. ២៥៦៨", 1)).isFalse()
        assertThat(KhmerSupport.quotePairs).containsEntry('«', '»')
    }

    @Test
    fun `without the phone's ICU the text stays one word`() {
        assertThat(KhmerSupport.wordStartOffset("ខ្ញុំទៅសាលា", "រ", "ៀន")).isEqualTo("ខ្ញុំទៅសាលា".length)
        assertThat(KhmerSupport.wordStarts("ខ្ញុំទៅ", 5)).containsExactly(0)
    }

    @Test
    fun `subscript TA headwords are indexed under DA, fonts need the khmer subset`() {
        val marks = KhmerSupport.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.replacements).containsExactly("${COENG}ត", "${COENG}ដ")
        assertThat(marks.marks).isEmpty()
        assertThat(KhmerSupport.fontSubsets).containsExactly("khmer")
        assertThat(KhmerSupport.ignoredFormTags).isEmpty()
        assertThat(KhmerSupport.ocrSpaceGap).isNotNull()
        assertThat(KhmerSupport.ocrScript).isEqualTo(OcrScript.KHMER)
        assertThat(KhmerSupport.wordSeparator).isEmpty()
        assertThat(KhmerSupport.searchResolution).isEqualTo(SearchResolution.LETTER)
    }

    private companion object {
        const val COENG = '្'
    }
}
