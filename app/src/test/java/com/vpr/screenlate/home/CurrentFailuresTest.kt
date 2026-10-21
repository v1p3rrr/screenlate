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
        val failures = listOf(failure(edited), failure(second), failure(first))
        val current = currentFailures(failures, listOf(first, AudioSource(AudioSourceType.JISHO), second))
        assertThat(current.map { it.source }).containsExactly(first, second).inOrder()
    }

    @Test
    fun `each language gets a card with the failures of its own lookups`() {
        val failures = listOf(failure(first), failure(second))
        val sources = listOf(Language.ENGLISH to listOf(AudioSource(AudioSourceType.JISHO)), Language.JAPANESE to listOf(second, first))

        assertThat(audioFailureCards(failures, sources, several = true)).containsExactly(
            HomeProblem.AudioSources(listOf(failure(second), failure(first)), Language.JAPANESE),
        )
        assertThat(audioFailureCards(failures, listOf(Language.JAPANESE to listOf(first)), several = false))
            .containsExactly(HomeProblem.AudioSources(listOf(failure(first))))
    }

    @Test
    fun `a shared source is listed under the languages whose lookups it failed in`() {
        // Korean is turned on first and lists the source too, but only Vietnamese and English lookups failed.
        val shared = listOf(
            Language.KOREAN to listOf(first),
            Language.VIETNAMESE to listOf(first, second),
            Language.ENGLISH to listOf(first),
            Language.GERMAN to listOf(first),
        )
        val failures = listOf(failure(first, Language.VIETNAMESE), failure(first, Language.ENGLISH), failure(second, Language.VIETNAMESE))

        assertThat(audioFailureCards(failures, shared, several = true)).containsExactly(
            HomeProblem.AudioSources(listOf(failure(first, Language.VIETNAMESE), failure(second, Language.VIETNAMESE)), Language.VIETNAMESE),
            HomeProblem.AudioSources(listOf(failure(first, Language.ENGLISH)), Language.ENGLISH),
        ).inOrder()
    }

    @Test
    fun `failures of a language that is not turned on are left out`() {
        val failures = listOf(failure(first, Language.GERMAN))
        assertThat(audioFailureCards(failures, listOf(Language.JAPANESE to listOf(first)), several = false)).isEmpty()
    }

    private fun failure(source: AudioSource, language: Language = Language.JAPANESE) = AudioSourceFailure(source, error, language)
}
