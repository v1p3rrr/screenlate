package com.vpr.screenlate.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDeck
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiModel
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.NoteTemplate
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnkiScreenState(
    /** Null until AnkiDroid was checked. */
    val availability: AnkiAvailability? = null,
    val decks: List<AnkiDeck> = emptyList(),
    val models: List<AnkiModel> = emptyList(),
    val fieldNames: List<String> = emptyList(),
    val settings: AnkiSettings = AnkiSettings(),
    /** The saved setup checked against AnkiDroid. */
    val status: AnkiStatus? = null,
    /** Markers offered for templates, including Yomitan's per-dictionary markers for installed dictionaries. */
    val markers: List<String> = FieldTemplate.markersFor(Language.JAPANESE),
    val error: String? = null,
)

@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val anki: AnkiDroid,
    private val notes: AnkiNotes,
    private val settingsRepository: AnkiSettingsRepository,
    dictionaries: DictionaryRepository,
) : ViewModel() {
    private val connection = MutableStateFlow(AnkiScreenState())
    private var refreshJob: Job? = null

    private val dictionaryMarkers = dictionaries.dictionaries.map { list ->
        val glossaries = list.filter { it.termCount > 0 }.map { FieldTemplate.singleGlossaryMarker(it.title) }
        val frequencies = list.filter { it.frequencyCount > 0 }.map { FieldTemplate.singleFrequencyNumberMarker(it.title) }
        (glossaries + frequencies).distinct()
    }

    val state: StateFlow<AnkiScreenState> = combine(
        connection,
        settingsRepository.settings,
        dictionaryMarkers,
    ) { connection, settings, dynamicMarkers ->
        connection.copy(
            settings = settings,
            markers = FieldTemplate.markersFor(Language.JAPANESE) + dynamicMarkers,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnkiScreenState())

    init {
        refresh()
    }

    /** Re-reads AnkiDroid's decks, note types and fields, e.g. after the permission was granted. */
    fun refresh() {
        // The latest check wins: an earlier one may have read the note type the user just changed.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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
                    status = notes.status(),
                )
            }.onSuccess { connection.value = it }
                .onFailure {
                    if (it is CancellationException) throw it
                    connection.value = AnkiScreenState(availability = availability, error = it.message)
                }
        }
    }

    fun selectDeck(deck: AnkiDeck) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(deckId = deck.id, deckName = deck.name) }
            refresh()
        }
    }

    /**
     * Switches the note type. The templates of the previous note type are kept for when it is selected again; a
     * note type used before gets its templates back, a new one gets suggested templates.
     */
    fun selectModel(model: AnkiModel) {
        viewModelScope.launch {
            val fields = anki.fields(model.id)
            // Every note type has a field; no fields means AnkiDroid did not answer, and switching now would drop the
            // templates saved for this note type.
            if (fields.isEmpty()) {
                refresh()
                return@launch
            }
            settingsRepository.update { settings ->
                val saved = settings.modelName
                    ?.let { settings.savedTemplates + (it to NoteTemplate(settings.fields, settings.overwriteModes)) }
                    ?: settings.savedTemplates
                val restored = saved[model.name]
                val suggested = FieldTemplate.guess(model.name, fields)
                settings.copy(
                    modelId = model.id,
                    modelName = model.name,
                    fields = fields.associateWith { restored?.fields?.get(it) ?: suggested[it].orEmpty() },
                    overwriteModes = restored?.overwriteModes.orEmpty(),
                    savedTemplates = saved - model.name,
                )
            }
            connection.value = connection.value.copy(fieldNames = fields)
            refresh()
        }
    }

    /**
     * Follows fields renamed, added or removed in AnkiDroid: templates of fields that still exist stay, new fields
     * get suggested templates.
     */
    fun updateFieldList() {
        viewModelScope.launch {
            val current = settingsRepository.current()
            val modelId = current.modelId ?: return@launch
            val fields = anki.fields(modelId)
            if (fields.isEmpty()) return@launch
            val suggested = FieldTemplate.guess(current.modelName.orEmpty(), fields)
            settingsRepository.update { settings ->
                settings.copy(
                    fields = fields.associateWith { settings.fields[it] ?: suggested[it].orEmpty() },
                    overwriteModes = settings.overwriteModes.filterKeys { it in fields },
                )
            }
            refresh()
        }
    }

    /** Replaces all field templates of the current note type with the suggested ones. */
    fun suggestTemplates() {
        val fields = connection.value.fieldNames
        updateSettings { settings ->
            settings.copy(fields = FieldTemplate.guess(settings.modelName.orEmpty(), fields))
        }
    }

    fun setFieldTemplate(field: String, template: String) =
        updateSettings { it.copy(fields = it.fields + (field to template)) }

    fun setOverwriteMode(field: String, mode: OverwriteMode) =
        updateSettings { it.copy(overwriteModes = it.overwriteModes + (field to mode)) }

    fun setTags(tags: String) = updateSettings { it.copy(tags = tags) }

    fun setDuplicateCheck(enabled: Boolean) = updateSettings { it.copy(duplicateCheck = enabled) }

    fun setDuplicateScope(scope: DuplicateScope) = updateSettings { it.copy(duplicateScope = scope) }

    fun setDuplicateAllModels(enabled: Boolean) = updateSettings { it.copy(duplicateAllModels = enabled) }

    fun setDuplicateBehavior(behavior: DuplicateBehavior) = updateSettings { it.copy(duplicateBehavior = behavior) }

    private fun updateSettings(transform: (AnkiSettings) -> AnkiSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
