package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutdatedWebViewTest {

    @Test
    fun `versions below 80 are flagged as outdated`() {
        assertThat(isOutdatedWebViewVersion("69.0.3497.100")).isTrue()
        assertThat(isOutdatedWebViewVersion("53.0.2785.124")).isTrue()
        assertThat(isOutdatedWebViewVersion("79.0.3945.136")).isTrue()
    }

    @Test
    fun `versions 80 and newer are not flagged`() {
        assertThat(isOutdatedWebViewVersion("80.0.3987.162")).isFalse()
        assertThat(isOutdatedWebViewVersion("120.0.6099.144")).isFalse()
    }

    @Test
    fun `null or invalid version strings are not flagged`() {
        assertThat(isOutdatedWebViewVersion(null)).isFalse()
        assertThat(isOutdatedWebViewVersion("")).isFalse()
        assertThat(isOutdatedWebViewVersion("invalid")).isFalse()
    }
}
