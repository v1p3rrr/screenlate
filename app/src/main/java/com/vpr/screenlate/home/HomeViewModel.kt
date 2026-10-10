package com.vpr.screenlate.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.AnkiProblem
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceFailure
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryRepair
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.languages.LanguageSwitch
import com.vpr.screenlate.languages.missingCategories
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DictionarySummary(val installed: Int = 0, val enabled: Int = 0, val importing: Boolean = false)

/** Deck and note type when Anki export is configured, null otherwise. */
data class AnkiSummary(val deck: String, val model: String)

/** Files of a downloaded or imported dictionary are gone; [catalogEntry] allows downloading it again. */
data class MissingDictionary(val dictionary: DictionaryEntity, val catalogEntry: CatalogEntry?)

/**
 * Failures of the configured [sources] in their order; those of sources edited or removed since, or only tested in
 * the source editor, are left out.
 */
internal fun currentFailures(failures: List<AudioSourceFailure>, sources: List<AudioSource>): List<AudioSourceFailure> =
    failures.filter { it.source in sources }.sortedBy { sources.indexOf(it.source) }

/** Imports not finished yet: how many, and the running one's name and percent, if known. */
data class InstallProgress(val queued: Int, val current: String?, val percent: Int?)

internal fun installProgress(tasks: List<ImportTask>): InstallProgress? {
    val unfinished = tasks.filter { !it.finished }.takeIf { it.isNotEmpty() } ?: return null
    val running = unfinished.firstOrNull { it.state != ImportTask.State.QUEUED }
    return InstallProgress(unfinished.size, running?.name?.takeIf { it.isNotEmpty() }, running?.percent)
}

/** Turning [language] off asks about its files, [filesBytes] on disk; null when it has none. */
data class TurnOffRequest(val language: Language, val filesBytes: Long?)

/** Something that broke without the user changing Screenlate's settings. */
sealed interface HomeProblem {
    /** A broken note setup; [language] names whose setup while several languages are turned on. */
    data class Anki(val problem: AnkiProblem, val language: Language? = null) : HomeProblem

    /** Downloaded or imported dictionaries whose files are gone, in one card. */
    data class MissingDictionaries(val dictionaries: List<MissingDictionary>) : HomeProblem

    /**
     * Turned-on languages without a dictionary they cannot work without, with the categories they lack (the main one:
     * no enabled dictionary with definitions); [several] languages are turned on. While dictionaries are [installing],
     * the card shows their progress too.
     */
    data class NoTermDictionaries(
        val languages: Map<Language, Set<CatalogCategory>>,
        val several: Boolean,
        val installing: InstallProgress? = null,
    ) : HomeProblem

    /** Every audio source that failed recently, in one card. */
    data class AudioSources(val failures: List<AudioSourceFailure>) : HomeProblem
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    private val repair: DictionaryRepair,
    private val bundled: BundledDictionaries,
    private val catalog: DictionaryCatalog,
    private val notes: AnkiNotes,
    private val audio: AudioFinder,
    private val audioSettings: AudioSettingsRepository,
    private val overlaySettings: OverlaySettingsRepository,
    private val dictionaryReset: DictionaryReset,
    ankiSettings: AnkiSettingsRepository,
    private val profiles: LanguageProfiles,
    private val switch: LanguageSwitch,
) : ViewModel() {
    val languages: StateFlow<LanguageProfilesState> = profiles.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profiles.cachedState ?: LanguageProfilesState.DEFAULT)

    fun setActive(language: Language) {
        viewModelScope.launch { profiles.setActive(language) }
    }

    /** Turns [language] on at once when its dictionaries are installed; otherwise [onSetup] opens its download screen. */
    fun add(language: Language, onSetup: (Language) -> Unit) {
        viewModelScope.launch {
            if (switch.needsDownloads(language)) onSetup(language) else switch.turnOn(language)
        }
    }

    private val mutableTurnOff = MutableStateFlow<TurnOffRequest?>(null)

    /** The language whose turn-off dialog is open, once its files are measured. */
    val turnOffRequest: StateFlow<TurnOffRequest?> = mutableTurnOff

    fun requestTurnOff(language: Language) {
        viewModelScope.launch { mutableTurnOff.value = TurnOffRequest(language, switch.filesSize(language)) }
    }

    fun cancelTurnOff() {
        mutableTurnOff.value = null
    }

    fun turnOff(language: Language, deleteFiles: Boolean) {
        mutableTurnOff.value = null
        viewModelScope.launch { switch.turnOff(language, deleteFiles) }
    }

    /** The catalog's mandatory categories; the copy on the phone is enough for them. */
    private val localCatalog = flow { emit(withContext(Dispatchers.IO) { catalog.localCatalog() }) }

    /** The active language's note setup. */
    val anki: StateFlow<AnkiSummary?> = profiles.active
        .flatMapLatest { ankiSettings.settings(it) }
        .map(::ankiSummary)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ankiSettings.cachedSettings(profiles.cachedState?.active ?: Language.JAPANESE)?.let(::ankiSummary),
        )

    val dictionaries: StateFlow<DictionarySummary> =
        combine(repository.dictionaries, imports.tasks) { dictionaries, tasks ->
            DictionarySummary(
                installed = dictionaries.size,
                enabled = dictionaries.count { it.enabled },
                importing = tasks.any { !it.finished },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.cachedDictionaries?.let { DictionarySummary(installed = it.size, enabled = it.count { d -> d.enabled }) }
                ?: DictionarySummary(),
        )

    /** Whether the bubble is set to show; with the service running, that is whether it is on the screen. */
    val bubbleVisible: StateFlow<Boolean> = overlaySettings.settings
        .map { it.bubbleVisible }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), overlaySettings.cachedSettings?.bubbleVisible ?: true)

    fun setBubbleVisible(visible: Boolean) {
        viewModelScope.launch { overlaySettings.setBubbleVisible(visible) }
    }

    /** Whether to tell, once, that a dictionary reset was given up after the app died during it twice. */
    val resetGaveUpUntold: StateFlow<Boolean> =
        dictionaryReset.gaveUpUntold.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markResetGaveUpTold() {
        viewModelScope.launch { dictionaryReset.markGaveUpTold() }
    }

    private val checked = MutableStateFlow<List<HomeProblem>>(emptyList())
    private var refreshJob: Job? = null

    /** Dictionaries downloaded again from the card; their files stay missing until the download ends. */
    private val downloading = mutableSetOf<Long>()

    private val mutableRemoveError = MutableStateFlow<String?>(null)

    /** Why removing a dictionary from the missing files card failed; cleared by the next removal or check. */
    val removeError: StateFlow<String?> = mutableRemoveError

    /** Problems found by the last [refresh], plus the languages without a dictionary they need, with the imports' progress. */
    val problems: StateFlow<List<HomeProblem>> =
        combine(checked, repository.dictionaries, imports.tasks, profiles.state, localCatalog) { found, all, tasks, languages, catalog ->
            val without = languages.turnedOn.associateWith { missingCategories(it, all, catalog) }.filterValues { it.isNotEmpty() }
            val noTerms = HomeProblem.NoTermDictionaries(without, languages.several, installProgress(tasks)).takeIf { without.isNotEmpty() }
            found.filterNot { it is HomeProblem.NoTermDictionaries } + listOfNotNull(noTerms)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Checks AnkiDroid, dictionary files and audio sources again; call when the screen is shown. */
    fun refresh() {
        // A check started earlier would otherwise finish last and bring back what a newer one found fixed.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableRemoveError.value = null
            val found = mutableListOf<HomeProblem>()
            val languages = profiles.current()
            for (language in languages.turnedOn) {
                (notes.status(language) as? AnkiStatus.Broken)?.let {
                    found += HomeProblem.Anki(it.problem, language.takeIf { languages.several })
                }
            }
            if (imports.tasks.first().all { it.finished }) downloading.clear()
            val missing = try {
                repair.repair()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dictionary check failed: ${e.javaClass.simpleName}")
                emptyList()
            }.filterNot { it.id in downloading }
            if (missing.isNotEmpty()) {
                val entries = try {
                    catalog.entries().first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                found += HomeProblem.MissingDictionaries(
                    missing.map { dictionary -> MissingDictionary(dictionary, entries.firstOrNull { it.matches(dictionary) }) },
                )
            }
            val sources = languages.turnedOn.flatMap { audioSettings.current(it).sources }.distinct()
            currentFailures(audio.recentFailures(), sources)
                .takeIf { it.isNotEmpty() }
                ?.let { found += HomeProblem.AudioSources(it) }
            checked.value = found
        }
    }

    fun downloadAgain(missing: MissingDictionary) {
        val entry = missing.catalogEntry ?: return
        imports.download(
            entry.downloadUrl,
            entry.title,
            indexUrl = entry.indexUrl.takeIf { entry.resolveLatest },
            replaces = missing.dictionary.id,
        )
        downloading += missing.dictionary.id
        resolved(missing)
    }

    fun remove(missing: MissingDictionary) {
        mutableRemoveError.value = null
        viewModelScope.launch {
            // Leaving the screen must not stop a delete halfway; finding the bundled archive takes a while.
            val deleted = withContext(NonCancellable) {
                try {
                    if (missing.dictionary.bundled) bundled.markDeleted(missing.dictionary.title)
                    repository.delete(missing.dictionary.id)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // E.g. a full storage, where the settings or the registry cannot be written; the card stays.
                    Log.w(TAG, "Removing a dictionary failed", e)
                    mutableRemoveError.value = e.message ?: e.javaClass.simpleName
                    false
                }
            }
            if (deleted) resolved(missing)
        }
    }

    private fun resolved(missing: MissingDictionary) {
        checked.value = checked.value.mapNotNull { problem ->
            if (problem !is HomeProblem.MissingDictionaries) return@mapNotNull problem
            HomeProblem.MissingDictionaries(problem.dictionaries - missing).takeIf { it.dictionaries.isNotEmpty() }
        }
    }

    fun dismissAudioFailures() {
        audio.clearFailures()
        checked.value = checked.value.filterNot { it is HomeProblem.AudioSources }
    }

    private companion object {
        const val TAG = "Home"
    }
}

private fun ankiSummary(settings: AnkiSettings): AnkiSummary? =
    if (settings.configured) AnkiSummary(settings.deckName.orEmpty(), settings.modelName.orEmpty()) else null
