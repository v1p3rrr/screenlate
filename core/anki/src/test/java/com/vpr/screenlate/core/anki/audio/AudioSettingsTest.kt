package com.vpr.screenlate.core.anki.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.MemoryDataStore
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

class AudioSettingsTest {
    @Test
    fun `every default source of every language is a known source type`() {
        for (language in Language.entries) {
            assertThat(AudioSettings.defaultSources(language).map { it.type.name })
                .containsExactlyElementsIn(language.support.defaultAudioSources)
                .inOrder()
        }
    }

    @Test
    fun `a source type this version does not know is left out, the rest stays`() = runBlocking<Unit> {
        val store = MemoryDataStore()
        store.edit {
            it[stringPreferencesKey("audio_settings")] =
                """{"sources":[{"type":"JISHO"},{"type":"SOME_NEW_SOURCE"},{"type":"URL","url":"https://a.example/{term}"}],""" +
                """"autoPlay":true,"volume":40}"""
        }
        val loaded = AudioSettingsRepository(store).current(Language.JAPANESE)
        assertThat(loaded.sources).containsExactly(
            AudioSource(AudioSourceType.JISHO),
            AudioSource(AudioSourceType.URL, "https://a.example/{term}"),
        ).inOrder()
        assertThat(loaded.autoPlay).isTrue()
        assertThat(loaded.volume).isEqualTo(40)
    }

    @Test
    fun `changes made at the same time are all kept`() = runBlocking<Unit> {
        val repository = AudioSettingsRepository(SlowReads())
        listOf(
            async { repository.update(Language.JAPANESE) { it.copy(volume = 30) } },
            async { repository.update(Language.JAPANESE) { it.copy(autoPlay = true) } },
            async { repository.update(Language.JAPANESE) { it.copy(sources = listOf(AudioSource(AudioSourceType.JISHO))) } },
        ).awaitAll()
        val settings = repository.current(Language.JAPANESE)
        assertThat(settings.volume).isEqualTo(30)
        assertThat(settings.autoPlay).isTrue()
        assertThat(settings.sources).containsExactly(AudioSource(AudioSourceType.JISHO))
    }

    @Test
    fun `each language has its own sources and shares auto play and volume`() = runBlocking<Unit> {
        val store = MemoryDataStore()
        val repository = AudioSettingsRepository(store)
        assertThat(repository.current(Language.ENGLISH).sources).isEqualTo(AudioSettings.defaultSources(Language.ENGLISH))
        repository.update(Language.ENGLISH) { it.copy(sources = listOf(AudioSource(AudioSourceType.WIKTIONARY)), volume = 50) }
        repository.update(Language.JAPANESE) { it.copy(autoPlay = true) }
        val english = repository.current(Language.ENGLISH)
        assertThat(english.sources).containsExactly(AudioSource(AudioSourceType.WIKTIONARY))
        assertThat(english.autoPlay).isTrue()
        assertThat(english.volume).isEqualTo(50)
        val japanese = repository.current(Language.JAPANESE)
        assertThat(japanese.sources).isEqualTo(AudioSettings.defaultSources(Language.JAPANESE))
        assertThat(japanese.volume).isEqualTo(50)
        assertThat(repository.volume()).isEqualTo(50)
        assertThat(store.data.first().asMap().keys.map { it.name }).containsExactly("audio_settings", "audio_settings_en", "audio_sources_chosen")
    }

    @Test
    fun `resetting the shared settings keeps every language's sources`() = runBlocking<Unit> {
        val repository = AudioSettingsRepository(MemoryDataStore())
        val japanese = listOf(AudioSource(AudioSourceType.WIKTIONARY))
        repository.update(Language.JAPANESE) { it.copy(sources = japanese, autoPlay = true, volume = 40) }
        repository.update(Language.ENGLISH) { it.copy(sources = emptyList()) }
        repository.resetShared()
        val after = repository.current(Language.JAPANESE)
        assertThat(after.sources).isEqualTo(japanese)
        assertThat(after.autoPlay).isFalse()
        assertThat(after.volume).isEqualTo(100)
        assertThat(repository.current(Language.ENGLISH).sources).isEmpty()
    }

    /** Reads suspend before answering, as a file store's do, so changes that read first would interleave. */
    private class SlowReads(private val store: MemoryDataStore = MemoryDataStore()) : DataStore<Preferences> by store {
        override val data: Flow<Preferences> = store.data.onStart { yield() }
    }
}
