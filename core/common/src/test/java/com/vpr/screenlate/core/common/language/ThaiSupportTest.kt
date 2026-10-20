package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThaiSupportTest {

    @Test
    fun `the lookup starts at the start of the ICU word under the aim`() {
        // ห้อง|สมุด
        assertThat(IcuThai.wordStartOffset("ห้อง", "ส", "มุด")).isEqualTo(0)
        assertThat(IcuThai.wordStartOffset("ห้องส", "ม", "ุด")).isEqualTo(1)
        assertThat(IcuThai.wordStartOffset("ห้องสม", "ุ", "ด")).isEqualTo(2)
        assertThat(IcuThai.wordStartOffset("ห", "้", "องสมุด")).isEqualTo(1)
        // โรงเรียน is one word: the text after the aim tells it from โรง|เ.
        assertThat(IcuThai.wordStartOffset("โรงเ", "ร", "ียน")).isEqualTo(4)
        assertThat(IcuThai.wordStartOffset("ราคา ", "1", "50")).isEqualTo(0)
        assertThat(IcuThai.wordStartOffset("ราคา", " ", "150")).isEqualTo(0)
    }

    @Test
    fun `the lookup also starts up to two ICU words before the aimed one, not across a space`() {
        // ผู้|หญิง|คน|นั้น|กำลัง|อ่าน
        assertThat(IcuThai.earlierStartOffset("ผู้หญิงคน", "นั้นกำลังอ่าน")).isEqualTo("หญิงคน".length)
        assertThat(IcuThai.earlierStartOffset("ห้อง", "สมุด")).isEqualTo("ห้อง".length)
        assertThat(IcuThai.earlierStartOffset("กรุงเทพฯ ", "เป็นเมืองหลวง")).isEqualTo(0)
        assertThat(IcuThai.earlierStartOffset("ราคา 150 ", "บาท")).isEqualTo(0)
        assertThat(IcuThai.earlierStartOffset("", "ห้องสมุด")).isEqualTo(0)
        assertThat(IcuThai.wordStarts("ผู้หญิงคนนั้นกำลังอ่าน", "ผู้หญิงคน".length)).containsExactly(0, 3, 7).inOrder()
    }

    @Test
    fun `shorter matches show only where ICU ends a word and never inside a cluster`() {
        // การ|ตลาด: การ stays, กา and ก go.
        assertThat(IcuThai.shownMatchLengths("การตลาด", setOf(1, 2, 3, 7))).containsExactly(3, 7)
        // ห้อง|สมุด: ห and ห้ go.
        assertThat(IcuThai.shownMatchLengths("ห้องสมุด", setOf(1, 2, 4, 8))).containsExactly(4, 8)
        // The longest match always shows unless it ends inside a cluster.
        assertThat(IcuThai.shownMatchLengths("ห้องสมุด", setOf(2))).containsExactly(2)
        assertThat(IcuThai.shownMatchLengths("กา", setOf(1, 2))).containsExactly(2)
        assertThat(IcuThai.shownMatchLengths("ห้อง", setOf(1))).isEmpty()
        assertThat(IcuThai.shownMatchLengths("เรียน", setOf(1, 5))).containsExactly(5)
    }

    @Test
    fun `a word starts at a letter or digit outside a cluster, not at the repetition or abbreviation mark`() {
        assertThat(IcuThai.lookupStart("เรียน", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(IcuThai.lookupStart("150", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(IcuThai.lookupStart("ฯลฯ", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(IcuThai.lookupStart("้อง", latinAsNative = false)).isNull()
        assertThat(IcuThai.lookupStart("าง", latinAsNative = false)).isNull()
        assertThat(IcuThai.lookupStart("ๆ ชอบ", latinAsNative = false)).isNull()
        assertThat(IcuThai.lookupStart("ฯ เป็น", latinAsNative = false)).isNull()
        assertThat(IcuThai.lookupStart(" ", latinAsNative = false)).isNull()
        assertThat(IcuThai.lookupStart("", latinAsNative = false)).isNull()
    }

    @Test
    fun `a space between Thai letters ends a sentence, beside marks, digits or Latin it does not`() {
        assertThat(ThaiSupport.endsSentence("ฉันชอบกิน ข้าวผัด", "ฉันชอบกิน".length)).isTrue()
        assertThat(ThaiSupport.endsSentence("กิน  ข้าว", "กิน ".length)).isTrue()
        assertThat(ThaiSupport.endsSentence("เด็ก ๆ ชอบ", "เด็ก".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("เด็ก ๆ ชอบ", "เด็ก ๆ".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("กรุงเทพฯ เป็น", "กรุงเทพฯ".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("ข้าว ฯลฯ", "ข้าว".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("ราคา 150 บาท", "ราคา".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("ชอบ iPhone มาก", "ชอบ".length)).isFalse()
        assertThat(ThaiSupport.endsSentence("ไป ๑๐ วัน", "ไป".length)).isFalse()
    }

    @Test
    fun `a period ends nothing after Thai letters`() {
        assertThat(ThaiSupport.endsSentence("พ.ศ. 2567", 1)).isFalse()
        assertThat(ThaiSupport.endsSentence("พ.ศ. 2567", 3)).isFalse()
        assertThat(ThaiSupport.endsSentence("ใช้ iPhone. ฉัน", "ใช้ iPhone".length)).isTrue()
        assertThat(ThaiSupport.endsSentence("ไปไหน?", "ไปไหน".length)).isTrue()
    }

    @Test
    fun `without the phone's ICU the text stays one word`() {
        // android.jar's stubs in unit tests; nothing fails.
        assertThat(ThaiSupport.wordStartOffset("ห้อง", "ส", "มุด")).isEqualTo(4)
        assertThat(ThaiSupport.wordStarts("ห้องสมุด", 4)).containsExactly(0)
        assertThat(ThaiSupport.shownMatchLengths("การตลาด", setOf(3, 7))).containsExactly(7)
    }

    @Test
    fun `classifier forms are left out, fonts need the thai subset, recordings come first`() {
        assertThat(ThaiSupport.ignoredFormTags).containsExactly("classifier")
        assertThat(ThaiSupport.fontSubsets).containsExactly("thai")
        assertThat(ThaiSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(ThaiSupport.wordSeparator).isEmpty()
        assertThat(ThaiSupport.searchResolution).isEqualTo(SearchResolution.LETTER)
    }
}
