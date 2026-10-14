package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioError
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceFailure
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.common.Language
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

    @Test
    fun `each language gets a card with its own failures and a shared source goes to the first`() {
        val failures = listOf(AudioSourceFailure(first, error), AudioSourceFailure(second, error))
        val sources = listOf(Language.ENGLISH to listOf(AudioSource(AudioSourceType.JISHO)), Language.JAPANESE to listOf(second, first))

        assertThat(audioFailureCards(failures, sources, several = true)).containsExactly(
            HomeProblem.AudioSources(listOf(AudioSourceFailure(second, error), AudioSourceFailure(first, error)), Language.JAPANESE),
        )
        val shared = listOf(Language.ENGLISH to listOf(first), Language.JAPANESE to listOf(second, first))
        assertThat(audioFailureCards(failures, shared, several = true)).containsExactly(
            HomeProblem.AudioSources(listOf(AudioSourceFailure(first, error)), Language.ENGLISH),
            HomeProblem.AudioSources(listOf(AudioSourceFailure(second, error)), Language.JAPANESE),
        ).inOrder()
        assertThat(audioFailureCards(failures, listOf(Language.JAPANESE to listOf(first)), several = false))
            .containsExactly(HomeProblem.AudioSources(listOf(AudioSourceFailure(first, error))))
    }
}
