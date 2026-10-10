package com.vpr.screenlate.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.audio.AudioCandidate
import com.vpr.screenlate.core.anki.audio.AudioError
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettings
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.Pronunciation
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.languages.ShownLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What one source offers for the test word.
 *
 * @property error a short reason when the source failed; null while loading or when it answered.
 */
data class SourceTest(
    val source: AudioSource,
    val loading: Boolean = true,
    val candidates: List<AudioCandidate> = emptyList(),
    val error: AudioError? = null,
)

/** A played test clip: which candidate, and whether it turned out to have no audio. */
data class PlayState(val candidateId: String? = null, val sourceIndex: Int = -1, val missing: Boolean = false)

/** Audio settings of the language the Anki page shows: its sources, and the auto play and volume all languages share. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    private val repository: AudioSettingsRepository,
    private val finder: AudioFinder,
    private val player: AudioPlayer,
    private val lookup: DictionaryLookup,
    profiles: LanguageProfiles,
) : ViewModel() {
    private val shown = ShownLanguage(profiles, viewModelScope)

    val settings: StateFlow<AudioSettings?> = shown.language
        .flatMapLatest { repository.settings(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.cachedSettings(shown.language.value))

    val testWord = MutableStateFlow(shown.language.value.support.audioTestWord)
    val tests = MutableStateFlow<List<SourceTest>>(emptyList())
    val played = MutableStateFlow(PlayState())

    /** The result of testing the source being edited in the dialog. */
    val dialogTest = MutableStateFlow<SourceTest?>(null)

    private var testJob: Job? = null
    private var dialogJob: Job? = null
    private var playJob: Job? = null

    /**
     * Shows [language]'s sources. A test word left as the previous language's sample becomes the new language's, and
     * earlier test results go.
     */
    fun show(language: Language) {
        val previous = shown.language.value
        if (language == previous) return
        shown.show(language)
        if (testWord.value.isBlank() || testWord.value == previous.support.audioTestWord) {
            testWord.value = language.support.audioTestWord
        }
        testJob?.cancel()
        tests.value = emptyList()
        played.value = PlayState()
        clearDialogTest()
    }

    fun setAutoPlay(enabled: Boolean) = update { it.copy(autoPlay = enabled) }

    fun setVolume(volume: Int) = update { it.copy(volume = volume.coerceIn(0, 100)) }

    /** Saves the source edited in the dialog; [index] null adds it at the end. */
    fun save(index: Int?, source: AudioSource) = update { audio ->
        val sources = if (index == null) {
            audio.sources + source
        } else {
            audio.sources.mapIndexed { i, old -> if (i == index) source else old }
        }
        audio.copy(sources = sources)
    }

    fun remove(index: Int) = update { audio -> audio.copy(sources = audio.sources.filterIndexed { i, _ -> i != index }) }

    fun move(index: Int, delta: Int) = update { audio ->
        val target = index + delta
        if (target !in audio.sources.indices) return@update audio
        audio.copy(sources = audio.sources.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun resetSources() {
        val language = shown.language.value
        update { it.copy(sources = AudioSettings.defaultSources(language)) }
    }

    /** Asks every configured source for the test word. */
    fun testAll() {
        val sources = settings.value?.sources.orEmpty()
        val language = shown.language.value
        testJob?.cancel()
        tests.value = sources.map { SourceTest(it) }
        played.value = PlayState()
        testJob = viewModelScope.launch {
            val (term, reading) = resolve(testWord.value, language)
            sources.forEachIndexed { index, source ->
                launch {
                    val result = finder.test(source, term, reading, language)
                    tests.value = tests.value.toMutableList().also { list ->
                        if (index in list.indices) {
                            list[index] = SourceTest(
                                source = source,
                                loading = false,
                                candidates = result.getOrDefault(emptyList()),
                                error = result.exceptionOrNull()?.let(AudioError::of),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Tests the source being edited and plays its first clip. */
    fun testInDialog(source: AudioSource) {
        dialogJob?.cancel()
        dialogTest.value = SourceTest(source)
        val language = shown.language.value
        dialogJob = viewModelScope.launch {
            val (term, reading) = resolve(testWord.value, language)
            val result = finder.test(source, term, reading, language)
            val candidates = result.getOrDefault(emptyList())
            dialogTest.value = SourceTest(
                source,
                loading = false,
                candidates = candidates,
                error = result.exceptionOrNull()?.let(AudioError::of),
            )
            candidates.firstOrNull()?.let { play(it, -1) }
        }
    }

    /** Also drops a test still running, so its result does not show up or play after the dialog changed. */
    fun clearDialogTest() {
        dialogJob?.cancel()
        dialogTest.value = null
    }

    /** The last clip asked for plays; an earlier one still downloading is dropped. */
    fun play(candidate: AudioCandidate, sourceIndex: Int) {
        playJob?.cancel()
        val language = shown.language.value
        playJob = viewModelScope.launch {
            played.value = PlayState(candidate.id, sourceIndex)
            val (term, reading) = resolve(testWord.value, language)
            if (candidate.isSpeech) {
                if (!player.play(Pronunciation.Speech(reading.ifEmpty { term }, language))) {
                    played.value = PlayState(candidate.id, sourceIndex, missing = true)
                }
                return@launch
            }
            val clip = finder.download(candidate, term, reading)
            if (clip == null) played.value = PlayState(candidate.id, sourceIndex, missing = true)
            else player.play(Pronunciation.Clip(clip))
        }
    }

    /** The test word's dictionary form and reading, as a lookup in [language] finds them. */
    private suspend fun resolve(word: String, language: Language): Pair<String, String> {
        val text = word.trim().ifEmpty { language.support.audioTestWord }
        val first = runCatching { lookup.lookup(text, language, scanLength = text.length, extraEntries = false) }
            .getOrNull()?.firstOrNull()
        return first?.term?.let { it.expression to it.reading } ?: (text to "")
    }

    private fun update(transform: (AudioSettings) -> AudioSettings) {
        val language = shown.language.value
        viewModelScope.launch { repository.update(language, transform) }
    }
}
