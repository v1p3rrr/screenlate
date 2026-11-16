package com.vpr.screenlate.core.common.network

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class GoogleDomainsTest {
    @Test
    fun japaneseUsesCoJp() {
        assertThat(GoogleDomains.domain(Language.JAPANESE)).isEqualTo("www.google.co.jp")
        assertThat(GoogleDomains.imagesUrl(Language.JAPANESE, "食べる"))
            .isEqualTo("https://www.google.co.jp/search?q=%E9%A3%9F%E3%81%B9%E3%82%8B&tbm=isch")
    }

    @Test
    fun russianUsesGoogleRu() {
        assertThat(GoogleDomains.domain(Language.RUSSIAN)).isEqualTo("www.google.ru")
        assertThat(GoogleDomains.imagesUrl(Language.RUSSIAN, "слово"))
            .isEqualTo("https://www.google.ru/search?q=%D1%81%D0%BB%D0%BE%D0%B2%D0%BE&tbm=isch")
    }

    @Test
    fun englishUsesGoogleCom() {
        assertThat(GoogleDomains.domain(Language.ENGLISH)).isEqualTo("www.google.com")
        assertThat(GoogleDomains.imagesUrl(Language.ENGLISH, "apple"))
            .isEqualTo("https://www.google.com/search?q=apple&tbm=isch")
    }

    @Test
    fun germanUsesGoogleDe() {
        assertThat(GoogleDomains.domain(Language.GERMAN)).isEqualTo("www.google.de")
    }

    @Test
    fun fallbackWithHlLanguageParam() {
        assertThat(GoogleDomains.domain(Language.CHINESE)).isEqualTo("www.google.com")
        assertThat(GoogleDomains.imagesUrl(Language.CHINESE, "词"))
            .isEqualTo("https://www.google.com/search?q=%E8%AF%8D&tbm=isch&hl=zh")
    }
}
