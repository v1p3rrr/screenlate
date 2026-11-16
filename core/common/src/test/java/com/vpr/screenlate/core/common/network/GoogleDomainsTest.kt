package com.vpr.screenlate.core.common.network

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class GoogleDomainsTest {
    @Test
    fun japaneseUsesCoJp() {
        assertThat(GoogleDomains.domain(Language.JAPANESE)).isEqualTo("www.google.co.jp")
    }

    @Test
    fun russianUsesGoogleRu() {
        assertThat(GoogleDomains.domain(Language.RUSSIAN)).isEqualTo("www.google.ru")
    }

    @Test
    fun englishUsesGoogleCom() {
        assertThat(GoogleDomains.domain(Language.ENGLISH)).isEqualTo("www.google.com")
    }

    @Test
    fun germanUsesGoogleDe() {
        assertThat(GoogleDomains.domain(Language.GERMAN)).isEqualTo("www.google.de")
    }

    @Test
    fun fallbackWithHlLanguageParam() {
        assertThat(GoogleDomains.domain(Language.CHINESE)).isEqualTo("www.google.com")
    }
}
