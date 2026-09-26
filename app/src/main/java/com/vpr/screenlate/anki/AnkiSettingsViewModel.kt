package com.vpr.screenlate.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDeck
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiModel
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioSettings
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnkiScreenState(
    val availability: AnkiAvailability = AnkiAvailability.NOT_INSTALLED,
    val decks: List<AnkiDeck> = emptyList(),
    val models: List<AnkiModel> = emptyList(),
    val fieldNames: List<String> = emptyList(),
    val settings: AnkiSettings = AnkiSettings(),
    val audio: AudioSettings = AudioSettings(),
    /** Markers offered for templates, including one glossary marker per installed term dictionary. */
    val markers: List<String> = FieldTemplate.MARKERS,
    val error: String? = null,
)

@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val anki: AnkiDroid,
    private val notes: AnkiNotes,
    private val settingsRepository: AnkiSettingsRepository,
    private val audioRepository: AudioSettingsRepository,
    dictionaries: DictionaryRepository,
) : ViewModel() {
    private val connection = MutableStateFlow(AnkiScreenState())

    private val dictionaryMarkers = dictionaries.dictionaries.map { list ->
        list.filter { it.termCount > 0 }.map { FieldTemplate.glossaryMarker(it.title) }.distinct()
    }

    val state: StateFlow<AnkiScreenState> = combine(
        connection,
        settingsRepository.settings,
        audioRepository.settings,
        dictionaryMarkers,
    ) { connection, settings, audio, glossaryMarkers ->
        connection.copy(settings = settings, audio = audio, markers = FieldTemplate.MARKERS + glossaryMarkers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnkiScreenState())

    init {
        refresh()
    }

    /** Re-reads AnkiDroid's decks, note types and fields, e.g. after the permission was granted. */
    fun refresh() {
        viewModelScope.launch {
            val availability = anki.availability()
            if (availability != AnkiAvailability.READY) {
                connection.value = AnkiScreenState(availability = availability)
                return@launch
            }
            runCatching {
                notes.invalidate()
                val modelId = settingsRepository.current().modelId
                AnkiScreenState(
                    availability = availability,
                    decks = anki.decks(),
                    models = anki.models(),
                    fieldNames = modelId?.let { anki.fields(it) }.orEmpty(),
                )
            }.onSuccess { connection.value = it }
                .onFailure { connection.value = AnkiScreenState(availability = availability, error = it.message) }
        }
    }

    fun selectDeck(deck: AnkiDeck) = updateSettings { it.copy(deckId = deck.id, deckName = deck.name) }

    /** Switches the note type and pre-fills templates for its fields from their names. */
    fun selectModel(model: AnkiModel) {
        viewModelScope.launch {
            val fields = runCatching { anki.fields(model.id) }.getOrDefault(emptyList())
            settingsRepository.update { settings ->
                val templates = fields.mapIndexed { index, name ->
                    name to (settings.fields[name] ?: FieldTemplate.guess(name, index))
                }.toMap()
                settings.copy(modelId = model.id, modelName = model.name, fields = templates)
            }
            connection.value = connection.value.copy(fieldNames = fields)
        }
    }

    fun setFieldTemplate(field: String, template: String) =
        updateSettings { it.copy(fields = it.fields + (field to template)) }

    fun setTags(tags: String) = updateSettings { it.copy(tags = tags) }

    fun setDuplicateCheck(enabled: Boolean) = updateSettings { it.copy(duplicateCheck = enabled) }

    fun setDuplicateScope(scope: DuplicateScope) = updateSettings { it.copy(duplicateScope = scope) }

    fun setDuplicateAllModels(enabled: Boolean) = updateSettings { it.copy(duplicateAllModels = enabled) }

    fun setDuplicateBehavior(behavior: DuplicateBehavior) = updateSettings { it.copy(duplicateBehavior = behavior) }

    fun setAutoPlay(enabled: Boolean) = updateAudio { it.copy(autoPlay = enabled) }

    fun addAudioSource(type: AudioSourceType) = updateAudio { it.copy(sources = it.sources + AudioSource(type)) }

    fun setAudioSourceUrl(index: Int, url: String) = updateAudio { audio ->
        audio.copy(sources = audio.sources.mapIndexed { i, source -> if (i == index) source.copy(url = url) else source })
    }

    fun removeAudioSource(index: Int) = updateAudio { audio ->
        audio.copy(sources = audio.sources.filterIndexed { i, _ -> i != index })
    }

    fun moveAudioSource(index: Int, delta: Int) = updateAudio { audio ->
        val target = index + delta
        if (target !in audio.sources.indices) return@updateAudio audio
        audio.copy(sources = audio.sources.toMutableList().apply { add(target, removeAt(index)) })
    }

    private fun updateSettings(transform: (AnkiSettings) -> AnkiSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        viewModelScope.launch { audioRepository.update(transform) }
    }
}
