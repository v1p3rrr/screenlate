package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioSourceTest {
    @Test
    fun `address is the host and port of a URL source`() {
        val source = AudioSource(AudioSourceType.CUSTOM_JSON, "http://localhost:8770/?term={term}&reading={reading}")
        assertThat(source.address).isEqualTo("localhost:8770")
    }

    @Test
    fun `address leaves out credentials and the path`() {
        val source = AudioSource(AudioSourceType.URL, " https://user:secret@audio.example.org/a/{term}.mp3")
        assertThat(source.address).isEqualTo("audio.example.org")
    }

    @Test
    fun `built-in sources and malformed templates have no address`() {
        assertThat(AudioSource(AudioSourceType.JISHO).address).isNull()
        assertThat(AudioSource(AudioSourceType.URL, "{term}.mp3").address).isNull()
    }
}
