package com.vpr.screenlate.core.common.network

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class LocalNetworkTest {
    @Test
    fun `private and link-local addresses are local`() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.1", "192.168.1.20", "169.254.3.4", "[fd00::1]", "fe80::1").forEach {
            assertWithMessage(it).that(LocalNetwork.isLocalHost(it)).isTrue()
        }
    }

    @Test
    fun `public addresses, loopback and domains are not local`() {
        listOf("8.8.8.8", "172.32.0.1", "127.0.0.1", "localhost", "::1", "0.0.0.0", "audio.example.org", "2001:db8::1").forEach {
            assertWithMessage(it).that(LocalNetwork.isLocalHost(it)).isFalse()
        }
    }

    @Test
    fun `local names`() {
        listOf("nas", "nas.local", "server.lan", "box.home.arpa", "media.internal").forEach {
            assertWithMessage(it).that(LocalNetwork.isLocalHost(it)).isTrue()
        }
    }

    @Test
    fun `urls and templates`() {
        assertThat(LocalNetwork.isLocalUrl("http://192.168.1.20:5050/?term={term}&reading={reading}")).isTrue()
        assertThat(LocalNetwork.isLocalUrl("http://user:pw@nas.local/a/{term}.mp3")).isTrue()
        assertThat(LocalNetwork.isLocalUrl("http://[fd12::5]:8080/{term}")).isTrue()
        assertThat(LocalNetwork.isLocalUrl("https://audio.example.org/?term={term}")).isFalse()
        assertThat(LocalNetwork.isLocalUrl("http://127.0.0.1:8770/?term={term}")).isFalse()
        assertThat(LocalNetwork.isLocalUrl("{term}.mp3")).isFalse()
    }
}
