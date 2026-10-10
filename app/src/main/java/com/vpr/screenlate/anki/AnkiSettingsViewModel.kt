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
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.isFor
import com.vpr.screenlate.languages.ShownLanguage
import com.vpr.screenlate.settings.SettingsReset
import com.vpr.screenlate.settings.SettingsSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnkiScreenState(
    val language: Language,
    /** Null until AnkiDroid was checked. */
    val availability: AnkiAvailability? = null,
    val decks: List<AnkiDeck> = emptyList(),
    val models: List<AnkiModel> = emptyList(),
    val fieldNames: List<String> = emptyList(),
    val settings: AnkiSettings = AnkiSettings(),
    /** The saved setup checked against AnkiDroid. */
    val status: AnkiStatus? = null,
    /** Markers offered for templates, including Yomitan's per-dictionary markers for installed dictionaries. */
    val markers: List<String> = FieldTemplate.markersFor(language),
    val error: String? = null,
    /** Shown from the last known state while AnkiDroid is asked again; the screen dims it and takes no taps. */
    val refreshing: Boolean = false,
)

/** The [shown] language's note setup; the translation switch is shared by every language. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val anki: AnkiDroid,
    private val notes: AnkiNotes,
    private val settingsRepository: AnkiSettingsRepository,
    private val settingsReset: SettingsReset,
    private val cache: AnkiConnectionCache,
    private val translationSettings: TranslationSettingsRepository,
    dictionaries: DictionaryRepository,
    profiles: LanguageProfiles,
) : ViewModel() {
    val shown = ShownLanguage(profiles, viewModelScope)

    /** Whether notes get `{sentence-translation}`; the Translation page has this switch too. */
    val translationInNotes: StateFlow<Boolean?> = translationSettings.settings.map { it.ankiField }
        .stateIn(viewModelScope, SharingStarted.Eagerly, translationSettings.cachedSettings?.ankiField)

    // Opens with AnkiDroid's last answer, or the first time with the saved setup laid out as if AnkiDroid answered;
    // either way the screen keeps its layout when the answer comes.
    private val connection = MutableStateFlow(opening(shown.language.value))
    private var refreshJob: Job? = null

    private val shownSettings = shown.language.flatMapLatest { language -> settingsRepository.settings(language).map { language to it } }

    val state: StateFlow<AnkiScreenState> = combine(
        connection,
        shownSettings,
        dictionaries.dictionaries,
    ) { connection, (language, settings), list ->
        // Yomitan's per-dictionary markers for the language's installed dictionaries.
        val own = list.filter { it.isFor(language) }
        val glossaries = own.filter { it.termCount > 0 }.map { FieldTemplate.singleGlossaryMarker(it.title) }
        val frequencies = own.filter { it.frequencyCount > 0 }.map { FieldTemplate.singleFrequencyNumberMarker(it.title) }
        val transcriptions = own.any { it.enabled && it.pitchCount > 0 }
        connection.copy(
            language = language,
            refreshing = connection.refreshing || connection.language != language,
            settings = settings,
            markers = FieldTemplate.markersFor(language, transcriptions) + (glossaries + frequencies).distinct(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        connection.value.copy(settings = settingsRepository.cachedSettings(shown.language.value) ?: AnkiSettings()),
    )

    /** AnkiDroid's last answer for [language]'s setup, or the saved setup laid out as if AnkiDroid answered. */
    private fun opening(language: Language): AnkiScreenState =
        cache.last[language]?.copy(refreshing = true) ?: AnkiScreenState(
            language = language,
            availability = AnkiAvailability.READY,
            fieldNames = settingsRepository.cachedSettings(language)?.fields?.keys?.toList().orEmpty(),
            refreshing = true,
        )

    /** Shows [language]'s setup, dimmed until AnkiDroid answered for it. */
    fun show(language: Language) {
        shown.show(language)
    }

    init {
        viewModelScope.launch {
            shown.language.collect { language ->
                connection.value = opening(language)
                refresh(language)
            }
        }
    }

    /** Re-reads AnkiDroid's decks, note types and fields, e.g. after the permission was granted. */
    fun refresh() = refresh(shown.language.value)

    private fun refresh(language: Language) {
        // The latest check wins: an earlier one may have read the note type the user just changed.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val availability = anki.availability()
            if (availability != AnkiAvailability.READY) {
                answer(language, AnkiScreenState(language = language, availability = availability))
                return@launch
            }
            runCatching {
                notes.invalidate()
                val modelId = settingsRepository.current(language).modelId
                AnkiScreenState(
                    language = language,
                    availability = availability,
                    decks = anki.decks(),
                    models = anki.models(),
                    fieldNames = modelId?.let { anki.fields(it) }.orEmpty(),
                    status = notes.status(language),
                )
            }.onSuccess { answer(language, it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    answer(language, AnkiScreenState(language = language, availability = availability, error = it.message))
                }
        }
    }

    private fun answer(language: Language, state: AnkiScreenState) {
        val answer = state.copy(language = language)
        cache.last[language] = answer
        if (connection.value.language == language) connection.value = answer
    }

    /**
     * Resets the page's shared settings and the shown language's; the setup is checked again, as no note type is
     * chosen any more.
     */
    fun resetSettings() {
        val language = shown.language.value
        viewModelScope.launch {
            withContext(NonCancellable) { settingsReset.reset(SettingsSection.ANKI, language) }
            refresh()
        }
    }

    fun setTranslationInNotes(enabled: Boolean) {
        viewModelScope.launch { translationSettings.setAnkiField(enabled) }
    }

    fun selectDeck(deck: AnkiDeck) {
        val language = shown.language.value
        viewModelScope.launch {
            settingsRepository.update(language) { it.copy(deckId = deck.id, deckName = deck.name) }
            refresh()
        }
    }

    /**
     * Switches the note type. The templates of the previous note type are kept for when it is selected again; a
     * note type used before gets its templates back, a new one gets suggested templates.
     */
    fun selectModel(model: AnkiModel) {
        val language = shown.language.value
        viewModelScope.launch {
            val fields = anki.fields(model.id)
            // Every note type has a field; no fields means AnkiDroid did not answer, and switching now would drop the
            // templates saved for this note type.
            if (fields.isEmpty()) {
                refresh()
                return@launch
            }
            settingsRepository.update(language) { settings ->
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
        val language = shown.language.value
        viewModelScope.launch {
            val current = settingsRepository.current(language)
            val modelId = current.modelId ?: return@launch
            val fields = anki.fields(modelId)
            if (fields.isEmpty()) return@launch
            val suggested = FieldTemplate.guess(current.modelName.orEmpty(), fields)
            settingsRepository.update(language) { settings ->
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
        val language = shown.language.value
        viewModelScope.launch { settingsRepository.update(language, transform) }
    }
}
