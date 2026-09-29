package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioError
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceFailure
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import org.junit.Test

class CurrentFailuresTest {
    private val error = AudioError(AudioError.Kind.REFUSED, "ConnectException")
    private val first = AudioSource(AudioSourceType.URL, "http://127.0.0.1:9/{term}.mp3")
    private val second = AudioSource(AudioSourceType.CUSTOM_JSON, "http://192.168.1.2:5050/?term={term}")
    private val edited = AudioSource(AudioSourceType.CUSTOM_JSON, "http://192.168.1.2:5051/?term={term}")

    @Test
    fun `failures follow the source order and skip sources that are gone`() {
        val failures = listOf(AudioSourceFailure(edited, error), AudioSourceFailure(second, error), AudioSourceFailure(first, error))
        val current = currentFailures(failures, listOf(first, AudioSource(AudioSourceType.JISHO), second))
        assertThat(current.map { it.source }).containsExactly(first, second).inOrder()
    }
}
