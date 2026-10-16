package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
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

    @Test
    fun `the Japanese sites are offered for Japanese only, the others for every language`() {
        val german = AudioSourceType.entries.filter { it.covers(Language.GERMAN) }
        assertThat(german).containsNoneOf(AudioSourceType.JAPANESE_POD_101, AudioSourceType.LANGUAGE_POD_101, AudioSourceType.JISHO)
        assertThat(german).containsAtLeast(AudioSourceType.WIKTIONARY, AudioSourceType.LINGUA_LIBRE, AudioSourceType.URL)
        assertThat(AudioSourceType.entries.filter { it.covers(Language.JAPANESE) }).isEqualTo(AudioSourceType.entries)
    }
}
