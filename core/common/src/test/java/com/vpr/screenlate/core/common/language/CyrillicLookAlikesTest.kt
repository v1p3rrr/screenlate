package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CyrillicLookAlikesTest {
    @Test
    fun `look-alikes in a word with Cyrillic letters become Cyrillic`() {
        // "мoлoкo" with Latin o, "Bыход" with a Latin B, "уxо," with a Latin x.
        assertThat(CyrillicLookAlikes.fix("\u043co\u043bo\u043ao")).isEqualTo("\u043c\u043e\u043b\u043e\u043a\u043e")
        assertThat(CyrillicLookAlikes.fix("B\u044b\u0445\u043e\u0434")).isEqualTo("\u0412\u044b\u0445\u043e\u0434")
        assertThat(CyrillicLookAlikes.fix("\u0443x\u043e,")).isEqualTo("\u0443\u0445\u043e,")
    }

    @Test
    fun `other Latin letters stay`() {
        // "Wi-Fi сеть" read as one word keeps W, i and F, which have no Cyrillic twin.
        assertThat(CyrillicLookAlikes.fix("Wi-Fi\u0441\u0435\u0442\u044c")).isEqualTo("Wi-Fi\u0441\u0435\u0442\u044c")
    }

    @Test
    fun `a word without Cyrillic letters stays as read`() {
        assertThat(CyrillicLookAlikes.fix("TOP")).isEqualTo("TOP")
        assertThat(CyrillicLookAlikes.fix("coffee")).isEqualTo("coffee")
        assertThat(CyrillicLookAlikes.fix("123")).isEqualTo("123")
    }

    @Test
    fun `a word of one letter becomes Cyrillic as well`() {
        // The prepositions у and В, the conjunction а, with punctuation beside them.
        assertThat(CyrillicLookAlikes.fix("y")).isEqualTo("у")
        assertThat(CyrillicLookAlikes.fix("B")).isEqualTo("В")
        assertThat(CyrillicLookAlikes.fix("a,")).isEqualTo("а,")
        // A letter with no twin, and two letters without a Cyrillic one, stay.
        assertThat(CyrillicLookAlikes.fix("i")).isEqualTo("i")
        assertThat(CyrillicLookAlikes.fix("ok")).isEqualTo("ok")
        assertThat(CyrillicLookAlikes.fix("i", mapOf('i' to 'і'))).isEqualTo("і")
    }

    @Test
    fun `only Cyrillic-script languages fix their words`() {
        assertThat(RussianSupport.ocrWord("\u043co\u0440e")).isEqualTo("\u043c\u043e\u0440\u0435")
        assertThat(EnglishSupport.ocrWord("\u043co\u0440e")).isEqualTo("\u043co\u0440e")
    }
}
